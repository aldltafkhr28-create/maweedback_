<?php
$pageTitle = 'Shop';
require_once 'includes/db.php';
require_once 'includes/header.php';

$category = isset($_GET['category']) ? trim($_GET['category']) : '';
$search   = isset($_GET['search'])   ? trim($_GET['search'])   : '';
$sort     = isset($_GET['sort'])     ? $_GET['sort']           : 'default';

$catLabels   = ['phones'=>'Phones','laptops'=>'Laptops','audio'=>'Audio','tablets'=>'Tablets','tv'=>'TV & Displays'];
$pageHeading = $category ? ($catLabels[$category] ?? ucfirst($category)) : ($search ? "Search: \"".htmlspecialchars($search)."\"" : 'All Products');

// ── Query from DB when available, otherwise filter static array ──
if ($conn) {
    $where  = [];
    $params = [];
    $types  = '';

    if ($category) {
        $where[]  = 'category = ?';
        $params[] = $category;
        $types   .= 's';
    }
    if ($search) {
        $where[]  = 'name LIKE ?';
        $params[] = '%' . $search . '%';
        $types   .= 's';
    }

    $orderBy = match($sort) {
        'low'  => 'price ASC',
        'high' => 'price DESC',
        'name' => 'name ASC',
        default => 'id ASC',
    };

    $sql = "SELECT * FROM products" . ($where ? ' WHERE ' . implode(' AND ', $where) : '') . " ORDER BY $orderBy";

    if ($params) {
        $stmt = $conn->prepare($sql);
        $stmt->bind_param($types, ...$params);
        $stmt->execute();
        $filtered = $stmt->get_result()->fetch_all(MYSQLI_ASSOC);
        $stmt->close();
    } else {
        $filtered = $conn->query($sql)->fetch_all(MYSQLI_ASSOC);
    }

    foreach ($filtered as &$p) {
        $p['price']     = (float)$p['price'];
        $p['old_price'] = (float)$p['old_price'];
        $p['rating']    = (int)$p['rating'];
    }
    unset($p);

} else {
    // Fallback: filter static $products array
    $filtered = array_filter($products, function($p) use ($category, $search) {
        if ($category && $p['category'] !== $category) return false;
        if ($search && stripos($p['name'], $search) === false) return false;
        return true;
    });

    if ($sort === 'low')       usort($filtered, fn($a,$b) => $a['price'] <=> $b['price']);
    elseif ($sort === 'high')  usort($filtered, fn($a,$b) => $b['price'] <=> $a['price']);
    elseif ($sort === 'name')  usort($filtered, fn($a,$b) => strcmp($a['name'], $b['name']));
}
?>

<!-- Page Header -->
<div class="page-header py-4 text-white">
    <div class="container">
        <nav aria-label="breadcrumb">
            <ol class="breadcrumb mb-1">
                <li class="breadcrumb-item"><a href="index.php" class="text-white-50">Home</a></li>
                <li class="breadcrumb-item active text-white">Shop</li>
            </ol>
        </nav>
        <h2 class="fw-bold mb-0"><?= $pageHeading ?></h2>
    </div>
</div>

<div class="container py-5">
    <div class="row g-4">
        <!-- Sidebar Filter -->
        <div class="col-lg-3">
            <div class="filter-card sticky-top" style="top:80px">
                <h6 class="fw-bold mb-3"><i class="fas fa-filter me-2 text-primary"></i>Filter</h6>
                <hr>
                <p class="fw-semibold mb-2">Category</p>
                <div class="list-group list-group-flush">
                    <a href="products.php" class="list-group-item list-group-item-action <?= !$category ? 'active' : '' ?>">All Products</a>
                    <?php foreach ($catLabels as $slug => $label): ?>
                    <a href="products.php?category=<?= $slug ?>" class="list-group-item list-group-item-action <?= $category===$slug ? 'active' : '' ?>"><?= $label ?></a>
                    <?php endforeach; ?>
                </div>
                <hr>
                <p class="fw-semibold mb-2">Sort By</p>
                <form method="GET">
                    <?php if ($category): ?><input type="hidden" name="category" value="<?= $category ?>">
                    <?php elseif ($search): ?><input type="hidden" name="search" value="<?= htmlspecialchars($search) ?>"><?php endif; ?>
                    <select name="sort" class="form-select form-select-sm mb-2" onchange="this.form.submit()">
                        <option value="default" <?= $sort==='default'?'selected':'' ?>>Default</option>
                        <option value="low"  <?= $sort==='low'?'selected':'' ?>>Price: Low to High</option>
                        <option value="high" <?= $sort==='high'?'selected':'' ?>>Price: High to Low</option>
                        <option value="name" <?= $sort==='name'?'selected':'' ?>>Name A-Z</option>
                    </select>
                </form>
                <hr>
                <form method="GET">
                    <?php if ($category): ?><input type="hidden" name="category" value="<?= $category ?>"><?php endif; ?>
                    <div class="input-group input-group-sm">
                        <input type="text" name="search" class="form-control" placeholder="Search..." value="<?= htmlspecialchars($search) ?>">
                        <button class="btn btn-primary"><i class="fas fa-search"></i></button>
                    </div>
                </form>
            </div>
        </div>

        <!-- Products Grid -->
        <div class="col-lg-9">
            <div class="d-flex justify-content-between align-items-center mb-4">
                <span class="text-muted"><?= count($filtered) ?> product<?= count($filtered)!==1?'s':'' ?> found</span>
                <a href="products.php" class="btn btn-sm btn-outline-secondary <?= (!$category&&!$search)?'d-none':'' ?>">
                    <i class="fas fa-times me-1"></i>Clear Filters
                </a>
            </div>

            <?php if (empty($filtered)): ?>
            <div class="text-center py-5">
                <i class="fas fa-search fa-4x text-muted mb-3"></i>
                <h4 class="text-muted">No products found</h4>
                <a href="products.php" class="btn btn-primary mt-2">Browse All</a>
            </div>
            <?php else: ?>
            <div class="row g-4">
                <?php foreach ($filtered as $p): ?>
                <div class="col-6 col-md-4">
                    <div class="product-card shadow-sm">
                        <div class="img-wrapper">
                            <img src="<?= $p['img'] ?>" alt="<?= $p['name'] ?>">
                            <?php if ($p['badge']): ?><span class="product-badge badge bg-danger"><?= $p['badge'] ?></span><?php endif; ?>
                            <div class="product-actions">
                                <button class="action-btn btn-wishlist" data-id="<?= $p['id'] ?>"><i class="far fa-heart"></i></button>
                                <a href="product.php?id=<?= $p['id'] ?>" class="action-btn"><i class="fas fa-eye text-primary"></i></a>
                            </div>
                        </div>
                        <div class="card-body">
                            <p class="product-name mb-1"><?= $p['name'] ?></p>
                            <div class="stars mb-2"><?= str_repeat('★', $p['rating']) . str_repeat('☆', 5-$p['rating']) ?></div>
                            <div class="d-flex align-items-center gap-2 mb-3">
                                <span class="price-current">$<?= $p['price'] ?></span>
                                <span class="price-old">$<?= $p['old_price'] ?></span>
                            </div>
                            <button class="btn btn-primary btn-add-cart"
                                data-id="<?= $p['id'] ?>" data-name="<?= $p['name'] ?>"
                                data-price="<?= $p['price'] ?>" data-img="<?= $p['img'] ?>">
                                <i class="fas fa-cart-plus me-2"></i>Add to Cart
                            </button>
                        </div>
                    </div>
                </div>
                <?php endforeach; ?>
            </div>
            <?php endif; ?>
        </div>
    </div>
</div>

<?php require_once 'includes/footer.php'; ?>
