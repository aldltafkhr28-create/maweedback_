<?php
require_once 'includes/db.php';
$id = isset($_GET['id']) ? (int)$_GET['id'] : 1;
$p  = getProductById($id);
if (!$p) { header('Location: products.php'); exit; }

$pageTitle = $p['name'];
$related   = array_filter($products, fn($x) => $x['category']===$p['category'] && $x['id']!==$id);
require_once 'includes/header.php';
?>

<!-- Breadcrumb -->
<div class="page-header py-3 text-white">
    <div class="container">
        <nav aria-label="breadcrumb">
            <ol class="breadcrumb mb-0">
                <li class="breadcrumb-item"><a href="index.php" class="text-white-50">Home</a></li>
                <li class="breadcrumb-item"><a href="products.php" class="text-white-50">Shop</a></li>
                <li class="breadcrumb-item active text-white"><?= $p['name'] ?></li>
            </ol>
        </nav>
    </div>
</div>

<div class="container py-5">
    <div class="row g-5">
        <!-- Images -->
        <div class="col-lg-5">
            <div class="main-img-wrapper text-center mb-3">
                <img id="mainImg" src="<?= $p['img'] ?>" alt="<?= $p['name'] ?>" class="img-fluid" style="max-height:380px; object-fit:contain;">
            </div>
            <div class="d-flex gap-2">
                <?php for ($i=1; $i<=3; $i++): ?>
                <img src="<?= $p['img'] ?>" alt="thumb" class="thumb-img <?= $i===1?'active':'' ?>"
                     onclick="document.getElementById('mainImg').src=this.src; document.querySelectorAll('.thumb-img').forEach(t=>t.classList.remove('active')); this.classList.add('active');">
                <?php endfor; ?>
            </div>
        </div>

        <!-- Info -->
        <div class="col-lg-7">
            <?php if ($p['badge']): ?><span class="badge bg-danger mb-2"><?= $p['badge'] ?></span><?php endif; ?>
            <h1 class="fw-bold mb-2"><?= $p['name'] ?></h1>
            <div class="stars fs-5 mb-2"><?= str_repeat('★', $p['rating']) . str_repeat('☆', 5-$p['rating']) ?>
                <span class="text-muted fs-6 ms-2">(<?= $p['rating'] * 24 ?> reviews)</span>
            </div>
            <div class="d-flex align-items-center gap-3 mb-4">
                <span class="fs-2 fw-bold text-primary">$<?= $p['price'] ?></span>
                <span class="fs-5 text-muted text-decoration-line-through">$<?= $p['old_price'] ?></span>
                <span class="badge bg-success fs-6"><?= round(($p['old_price']-$p['price'])/$p['old_price']*100) ?>% OFF</span>
            </div>

            <hr>
            <div class="mb-3">
                <p class="text-muted"><i class="fas fa-check-circle text-success me-2"></i>In Stock — Ships within 24 hours</p>
                <p class="text-muted"><i class="fas fa-shield-alt text-primary me-2"></i>1-Year Warranty Included</p>
                <p class="text-muted"><i class="fas fa-undo text-warning me-2"></i>30-Day Free Returns</p>
            </div>
            <hr>

            <div class="mb-4">
                <label class="fw-semibold mb-2">Quantity</label>
                <div class="input-group" style="width:150px">
                    <button class="btn btn-outline-secondary" onclick="if(qtyInput.value>1)qtyInput.value--">-</button>
                    <input type="number" id="qtyInput" class="form-control text-center" value="1" min="1" max="99">
                    <button class="btn btn-outline-secondary" onclick="qtyInput.value++">+</button>
                </div>
            </div>

            <div class="d-flex gap-3 flex-wrap">
                <button class="btn btn-primary btn-lg px-5 btn-add-cart"
                    data-id="<?= $p['id'] ?>" data-name="<?= $p['name'] ?>"
                    data-price="<?= $p['price'] ?>" data-img="<?= $p['img'] ?>">
                    <i class="fas fa-cart-plus me-2"></i>Add to Cart
                </button>
                <button class="btn btn-outline-secondary btn-lg btn-wishlist" data-id="<?= $p['id'] ?>">
                    <i class="far fa-heart me-2"></i>Wishlist
                </button>
            </div>
        </div>
    </div>

    <!-- Tabs: Description / Specs / Reviews -->
    <div class="mt-5">
        <ul class="nav nav-tabs" id="productTabs">
            <li class="nav-item"><a class="nav-link active" data-bs-toggle="tab" href="#desc">Description</a></li>
            <li class="nav-item"><a class="nav-link" data-bs-toggle="tab" href="#specs">Specifications</a></li>
            <li class="nav-item"><a class="nav-link" data-bs-toggle="tab" href="#reviews">Reviews</a></li>
        </ul>
        <div class="tab-content bg-white p-4 border border-top-0 rounded-bottom">
            <div class="tab-pane fade show active" id="desc">
                <p>Experience the next level of technology with the <strong><?= $p['name'] ?></strong>. Designed for performance and built for everyday use, this product delivers an outstanding experience. Whether you're working, gaming, or staying connected, it has everything you need.</p>
                <ul>
                    <li>Premium build quality with sleek design</li>
                    <li>High-performance components for smooth operation</li>
                    <li>Extended battery life for all-day use</li>
                    <li>Intuitive interface and easy setup</li>
                </ul>
            </div>
            <div class="tab-pane fade" id="specs">
                <table class="table table-bordered table-sm">
                    <tbody>
                        <tr><th width="200">Category</th><td><?= ucfirst($p['category']) ?></td></tr>
                        <tr><th>Price</th><td>$<?= $p['price'] ?></td></tr>
                        <tr><th>Rating</th><td><?= $p['rating'] ?>/5 Stars</td></tr>
                        <tr><th>Warranty</th><td>1 Year</td></tr>
                        <tr><th>Availability</th><td><span class="badge bg-success">In Stock</span></td></tr>
                        <tr><th>Shipping</th><td>Free (orders over $50)</td></tr>
                    </tbody>
                </table>
            </div>
            <div class="tab-pane fade" id="reviews">
                <?php for ($i=0; $i<3; $i++):
                    $names = ['Ahmed M.','Sara K.','John D.']; $texts=['Great product, exactly as described!','Fast shipping and perfect quality.','Highly recommended, great value!'];
                ?>
                <div class="d-flex gap-3 mb-3 p-3 bg-light rounded">
                    <div class="rounded-circle bg-primary d-flex align-items-center justify-content-center text-white fw-bold" style="width:45px;height:45px;flex-shrink:0"><?= $names[$i][0] ?></div>
                    <div><div class="fw-semibold"><?= $names[$i] ?></div><div class="stars small">★★★★★</div><p class="mb-0 text-muted small"><?= $texts[$i] ?></p></div>
                </div>
                <?php endfor; ?>
            </div>
        </div>
    </div>

    <!-- Related Products -->
    <?php if (!empty($related)): ?>
    <div class="mt-5">
        <h4 class="fw-bold mb-4">Related <span class="text-primary">Products</span></h4>
        <div class="row g-4">
            <?php foreach (array_slice($related, 0, 4) as $r): ?>
            <div class="col-6 col-md-3">
                <div class="product-card shadow-sm">
                    <div class="img-wrapper">
                        <img src="<?= $r['img'] ?>" alt="<?= $r['name'] ?>">
                    </div>
                    <div class="card-body">
                        <p class="product-name mb-1"><?= $r['name'] ?></p>
                        <div class="d-flex align-items-center gap-2 mb-2">
                            <span class="price-current">$<?= $r['price'] ?></span>
                        </div>
                        <a href="product.php?id=<?= $r['id'] ?>" class="btn btn-sm btn-outline-primary w-100">View Product</a>
                    </div>
                </div>
            </div>
            <?php endforeach; ?>
        </div>
    </div>
    <?php endif; ?>
</div>

<?php require_once 'includes/footer.php'; ?>
