<?php
$pageTitle = 'Home';
require_once 'includes/db.php';
require_once 'includes/header.php';
?>

<!-- Hero Section -->
<section class="hero-section d-flex align-items-center">
    <div class="container py-5">
        <div class="row align-items-center">
            <div class="col-lg-6 text-white">
                <span class="badge hero-badge px-3 py-2 mb-3 fs-6">🔥 Summer Sale — Up to 40% OFF</span>
                <h1 class="display-4 fw-bold mb-3">Next-Level<br><span class="text-warning">Tech Deals</span></h1>
                <p class="lead text-white-50 mb-4">Shop the latest smartphones, laptops, and gadgets at unbeatable prices. Free shipping on orders over $50.</p>
                <div class="d-flex gap-3 flex-wrap">
                    <a href="products.php" class="btn btn-warning btn-lg fw-bold px-4">Shop Now <i class="fas fa-arrow-right ms-2"></i></a>
                    <a href="products.php?category=phones" class="btn btn-outline-light btn-lg px-4">View Deals</a>
                </div>
            </div>
            <div class="col-lg-6 text-center mt-4 mt-lg-0">
                <img src="https://placehold.co/480x360/ffffff/0d6efd?text=TechStore+2026" alt="Hero" class="img-fluid rounded-4 shadow-lg">
            </div>
        </div>
    </div>
</section>

<!-- Stats Banner -->
<section class="stats-banner py-4">
    <div class="container">
        <div class="row text-center text-white g-3">
            <div class="col-6 col-md-3 stat-item">
                <div class="stat-number">50K+</div><div class="stat-label"><i class="fas fa-users me-1"></i>Happy Customers</div>
            </div>
            <div class="col-6 col-md-3 stat-item">
                <div class="stat-number">500+</div><div class="stat-label"><i class="fas fa-box me-1"></i>Products</div>
            </div>
            <div class="col-6 col-md-3 stat-item">
                <div class="stat-number">24/7</div><div class="stat-label"><i class="fas fa-headset me-1"></i>Support</div>
            </div>
            <div class="col-6 col-md-3 stat-item">
                <div class="stat-number">Free</div><div class="stat-label"><i class="fas fa-truck me-1"></i>Shipping $50+</div>
            </div>
        </div>
    </div>
</section>

<!-- Categories -->
<section class="py-5">
    <div class="container">
        <div class="text-center mb-4">
            <h2 class="section-title">Shop by <span>Category</span></h2>
            <div class="title-line mt-2 mb-3"></div>
        </div>
        <div class="row g-3">
            <?php
            $cats = [
                ['phones','Phones','fas fa-mobile-alt','bg-primary bg-opacity-10 text-primary','#0d6efd'],
                ['laptops','Laptops','fas fa-laptop','bg-success bg-opacity-10 text-success','#198754'],
                ['audio','Audio','fas fa-headphones','bg-warning bg-opacity-10 text-warning','#ffc107'],
                ['tablets','Tablets','fas fa-tablet-alt','bg-info bg-opacity-10 text-info','#0dcaf0'],
                ['tv','TV & Displays','fas fa-tv','bg-danger bg-opacity-10 text-danger','#dc3545'],
            ];
            foreach ($cats as [$slug, $label, $icon, $bg, $color]):
            ?>
            <div class="col-6 col-md-4 col-lg-2">
                <a href="products.php?category=<?= $slug ?>" class="category-card shadow-sm">
                    <div class="category-icon <?= $bg ?>"><i class="<?= $icon ?>"></i></div>
                    <div class="fw-semibold"><?= $label ?></div>
                </a>
            </div>
            <?php endforeach; ?>
            <div class="col-6 col-md-4 col-lg-2">
                <a href="products.php" class="category-card shadow-sm">
                    <div class="category-icon bg-dark bg-opacity-10 text-dark"><i class="fas fa-th"></i></div>
                    <div class="fw-semibold">All Products</div>
                </a>
            </div>
        </div>
    </div>
</section>

<!-- Featured Products -->
<section class="py-5 bg-white">
    <div class="container">
        <div class="d-flex justify-content-between align-items-center mb-4">
            <div>
                <h2 class="section-title">Featured <span>Products</span></h2>
                <div class="title-line mt-2"></div>
            </div>
            <a href="products.php" class="btn btn-outline-primary">View All <i class="fas fa-arrow-right ms-1"></i></a>
        </div>
        <div class="row g-4">
            <?php foreach (array_slice($products, 0, 4) as $p): ?>
            <div class="col-6 col-md-4 col-lg-3">
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
    </div>
</section>

<!-- Promo Banner -->
<section class="py-5">
    <div class="container">
        <div class="row g-3">
            <div class="col-md-6">
                <div class="rounded-4 p-4 text-white d-flex align-items-center gap-4" style="background: linear-gradient(135deg,#0d6efd,#0a58ca);">
                    <div>
                        <h4 class="fw-bold">New Phones</h4>
                        <p class="mb-2 text-white-50">Latest flagship models</p>
                        <a href="products.php?category=phones" class="btn btn-warning btn-sm fw-bold">Shop Phones</a>
                    </div>
                    <i class="fas fa-mobile-alt fa-4x ms-auto opacity-25"></i>
                </div>
            </div>
            <div class="col-md-6">
                <div class="rounded-4 p-4 text-white d-flex align-items-center gap-4" style="background: linear-gradient(135deg,#198754,#146c43);">
                    <div>
                        <h4 class="fw-bold">Power Laptops</h4>
                        <p class="mb-2 text-white-50">Work & gaming machines</p>
                        <a href="products.php?category=laptops" class="btn btn-warning btn-sm fw-bold">Shop Laptops</a>
                    </div>
                    <i class="fas fa-laptop fa-4x ms-auto opacity-25"></i>
                </div>
            </div>
        </div>
    </div>
</section>

<!-- All Products Preview -->
<section class="py-5 bg-white">
    <div class="container">
        <div class="d-flex justify-content-between align-items-center mb-4">
            <div>
                <h2 class="section-title">More <span>Products</span></h2>
                <div class="title-line mt-2"></div>
            </div>
            <a href="products.php" class="btn btn-outline-primary">View All</a>
        </div>
        <div class="row g-4">
            <?php foreach (array_slice($products, 4) as $p): ?>
            <div class="col-6 col-md-4 col-lg-3">
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
    </div>
</section>

<?php require_once 'includes/footer.php'; ?>
