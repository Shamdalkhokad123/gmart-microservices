package com.gmart.config;

import com.gmart.entity.Category;
import com.gmart.entity.Product;
import com.gmart.entity.CatalogStore;
import com.gmart.repository.CatalogStoreRepository;
import com.gmart.repository.CategoryRepository;
import com.gmart.repository.ProductRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/** Seeds the demo catalogue on first start. Development only: enabled by gmart.seed.demo-data=true (the dev profile). */
@Component
@ConditionalOnProperty(name = "gmart.seed.demo-data", havingValue = "true")
public class DataInitializer implements CommandLineRunner {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final CatalogStoreRepository storeRepository;

    public DataInitializer(CategoryRepository categoryRepository, ProductRepository productRepository,
                           CatalogStoreRepository storeRepository) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
        this.storeRepository = storeRepository;
    }

    /** Demo stores, same ids as store-service's seed: 1 groceries, 2 snacks/drinks/frozen. */
    private void seedStores() {
        if (storeRepository.count() == 0) {
            storeRepository.save(new CatalogStore(1, "GMart Indiranagar", true));
            storeRepository.save(new CatalogStore(2, "Fresh Basket Koramangala", true));
        }
    }

    @Override
    public void run(String... args) {
        seedStores();
        seedCategories();
        seedProducts();
    }

    private static String img(String id) {
        return "https://images.unsplash.com/photo-" + id + "?auto=format&fit=crop&w=600&q=80";
    }

    private void seedCategories() {
        if (categoryRepository.count() == 0) {
            categoryRepository.saveAll(List.of(
                new Category("Fruits & Vegetables", "Fresh fruits and farm vegetables, sourced every morning.", img("1610348725531-843dff563e2c")),
                new Category("Dairy, Bread & Eggs", "Milk, butter, yogurt, breads and farm eggs.", img("1628088062854-d1870b4553da")),
                new Category("Drinks & Juices", "Juices, iced teas, coolers and coffee.", img("1621506289937-a8e4df240d0b")),
                new Category("Snacks & Munchies", "Popcorn, nachos and cookies.", img("1513456852971-30c0b8199d4d")),
                new Category("Sweets & Ice Creams", "Chocolates, desserts and frozen treats.", img("1497034825429-c343d7c6a68f")),
                new Category("Atta, Rice & Dal", "Rice, wheat, pulses and breakfast grains.", img("1586201375761-83865001e31c")),
                new Category("Masala, Oil & More", "Spices, masalas and cooking oils.", img("1596040033229-a9821ebd058d")),
                new Category("Fish & Seafood", "Fresh, cleaned and ready to cook.", img("1565680018434-b513d5e5fd47")),
                new Category("Instant & Frozen Food", "Noodles, pasta, ready meals and frozen pizza.", img("1612929633738-8fe44f7ec841")),
                new Category("Home & Personal Care", "Soaps, detergents and household essentials.", img("1600857544200-b2f666a9a2ec"))
            ));
        }
    }

    private void seedProducts() {
        if (productRepository.count() == 0) {
            String fv = "Fruits & Vegetables", dbe = "Dairy, Bread & Eggs", dj = "Drinks & Juices", sm = "Snacks & Munchies",
                   si = "Sweets & Ice Creams", ard = "Atta, Rice & Dal", mo = "Masala, Oil & More", fs = "Fish & Seafood",
                   inf = "Instant & Frozen Food", hp = "Home & Personal Care";
            List<Product> all = List.of(
                // Fruits & Vegetables
                new Product("Shimla Apple", "Crisp, juicy apples from Himachal orchards.", fv, 149, 180f, "4 pcs (approx. 600 g)", 40, img("1560806887-1e4cd0b6cbd6")),
                new Product("Robusta Banana", "Naturally ripened, rich in potassium.", fv, 49, 60f, "6 pcs", 60, img("1571771894821-ce9b6c11b08e")),
                new Product("Hybrid Tomato", "Firm, tangy tomatoes for curries and salads.", fv, 24, 32f, "500 g", 80, img("1592924357228-91a4daadcfea")),
                new Product("Onion", "Everyday red onions, cleaned and sorted.", fv, 39, 52f, "1 kg", 80, img("1618512496248-a07fe83aa8cb")),
                new Product("Potato", "Versatile potatoes for fries, sabzi and more.", fv, 35, 45f, "1 kg", 80, img("1518977676601-b53f82aba655")),
                new Product("Orange Carrot", "Sweet, crunchy carrots.", fv, 32, 40f, "500 g", 50, img("1598170845058-32b9d6a5da37")),
                new Product("Palak (Spinach)", "Fresh green leaves, washed and bunched.", fv, 19, 25f, "250 g", 30, img("1576045057995-568f588f82fb")),
                new Product("Strawberry", "Sweet, bright red berries.", fv, 89, 120f, "200 g", 3, img("1464965911861-746a04b4bca6")),
                new Product("Black Grapes", "Seedless, sweet and juicy.", fv, 79, 99f, "500 g", 25, img("1537640538966-79f369143f8f")),
                new Product("Lemon", "Juicy lemons for nimbu pani and cooking.", fv, 25, 30f, "250 g", 50, img("1590502593747-42a996133562")),
                new Product("Hass Avocado", "Creamy, ready to eat in 2-3 days.", fv, 129, 169f, "1 pc", 12, img("1523049673857-eb18f1d7b578")),
                new Product("Alphonso Mango", "Ratnagiri Alphonso, sweet and aromatic. Seasonal.", fv, 349, 450f, "1 kg (approx. 4 pcs)", 0, img("1553279768-865429fa0078")),
                new Product("Nagpur Orange", "Juicy, easy-peel oranges.", fv, 89, 110f, "1 kg", 35, img("1536657464919-892534f60d6e")),
                new Product("Watermelon", "Sweet and refreshing, kiran variety.", fv, 79, 95f, "1 pc (approx. 2.5 kg)", 15, img("1587049352846-4a222e784d38")),
                // Dairy, Bread & Eggs
                new Product("Full Cream Milk", "Pasteurised, rich and creamy.", dbe, 68, 68f, "1 L", 100, img("1550583724-b2692b85b150")),
                new Product("Farm Fresh Brown Eggs", "Protein-rich eggs from free-range hens.", dbe, 59, 72f, "6 pcs", 60, img("1516448620398-c5f44bf9f441")),
                new Product("Salted Butter", "Made from fresh cream.", dbe, 58, 60f, "100 g", 50, img("1589985270826-4b7bb135bc9d")),
                new Product("Strawberry Yogurt", "Thick yogurt with real fruit.", dbe, 45, 50f, "2 x 100 g", 40, img("1488477181946-6428a0291777")),
                new Product("Sourdough Loaf", "Slow-fermented, crusty artisan bread.", dbe, 129, 150f, "400 g", 10, img("1589367920969-ab8e050bbb04")),
                new Product("Butter Croissant", "Flaky, baked fresh every morning.", dbe, 99, 120f, "Pack of 2", 20, img("1555507036-ab1f4038808a")),
                new Product("Multigrain Bread", "Soft bread with seeds and whole grains.", dbe, 55, 60f, "400 g", 40, img("1509440159596-0249088772ff")),
                // Drinks & Juices
                new Product("Fresh Orange Juice", "100% juice, no added sugar.", dj, 129, 160f, "1 L", 30, img("1621506289937-a8e4df240d0b")),
                new Product("Iced Lemon Tea", "Refreshing tea with a twist of lemon.", dj, 45, 50f, "300 ml", 50, img("1556679343-c7306c1976bc")),
                new Product("Mint Lemonade", "Fizzy lemon cooler with fresh mint.", dj, 35, 40f, "250 ml", 50, img("1621263764928-df1444c5e859")),
                new Product("Arabica Coffee Beans", "Medium roast with chocolate notes.", dj, 349, 450f, "250 g", 15, img("1559056199-641a0ac8b55e")),
                // Snacks & Munchies
                new Product("Classic Salted Popcorn", "Light, crunchy and perfectly salted.", sm, 40, 50f, "60 g", 60, img("1578849278619-e73505e9610f")),
                new Product("Choco Chip Cookies", "Buttery cookies loaded with chocolate chips.", sm, 99, 125f, "200 g", 40, img("1499636136210-6f4ee915583e")),
                new Product("Cheesy Nachos", "Corn tortilla chips with cheese dip.", sm, 79, 99f, "150 g", 40, img("1513456852971-30c0b8199d4d")),
                // Sweets & Ice Creams
                new Product("Dark Chocolate with Almonds", "55% cocoa with roasted almonds.", si, 149, 175f, "100 g", 30, img("1606312619070-d48b4c652a52")),
                new Product("Assorted Chocolate Truffles", "Gift box of 16 handmade truffles.", si, 299, 399f, "Box of 16", 10, img("1599599810769-bcde5a160d32")),
                new Product("Vanilla Ice Cream Cone", "Creamy vanilla in a crispy cone.", si, 40, 45f, "110 ml", 50, img("1497034825429-c343d7c6a68f")),
                new Product("Assorted Ice Cream Cones", "Four flavours to share.", si, 160, 200f, "Pack of 4", 20, img("1501443762994-82bd5dace89a")),
                new Product("Choco Crunch Ice Cream Bars", "Chocolate-coated bars with crunchy sprinkles.", si, 199, 240f, "Pack of 4", 20, img("1590080875515-8a3a8dc5735e")),
                // Atta, Rice & Dal
                new Product("Basmati Rice", "Long-grain, aged basmati.", ard, 129, 165f, "1 kg", 50, img("1586201375761-83865001e31c")),
                new Product("Kabuli Chana", "Large, premium white chickpeas.", ard, 79, 95f, "500 g", 40, img("1515543904379-3d757afe72e4")),
                new Product("Sharbati Whole Wheat", "Golden MP Sharbati wheat grains.", ard, 59, 70f, "1 kg", 40, img("1574323347407-f5e1ad6d020b")),
                new Product("Rolled Oats", "Wholegrain oats for a healthy breakfast.", ard, 99, 130f, "500 g", 35, img("1614961233913-a5113a4a34ed")),
                // Masala, Oil & More
                new Product("Extra Virgin Olive Oil", "Cold pressed, for dressings and light cooking.", mo, 549, 699f, "500 ml", 20, img("1474979266404-7eaacbcd87c5")),
                new Product("Red Chilli Powder", "Bright colour, medium heat.", mo, 69, 85f, "200 g", 40, img("1607672632458-9eb56696346b")),
                new Product("Whole Spices Combo", "Cardamom, cloves, cinnamon, star anise and more.", mo, 99, 120f, "100 g", 30, img("1596040033229-a9821ebd058d")),
                new Product("Garam Masala", "Aromatic blend of roasted spices.", mo, 65, 80f, "100 g", 40, img("1615485500704-8e990f9900f7")),
                // Fish & Seafood
                new Product("Atlantic Salmon Fillet", "Boneless, skin-on fillet.", fs, 549, 650f, "250 g", 8, img("1599084993091-1cb5c0721cc6")),
                new Product("Tiger Prawns (Cleaned)", "Deveined, ready to cook.", fs, 329, 399f, "250 g", 10, img("1565680018434-b513d5e5fd47")),
                // Instant & Frozen Food
                new Product("Hakka Noodles", "Ready in 5 minutes.", inf, 55, 65f, "280 g", 50, img("1612929633738-8fe44f7ec841")),
                new Product("Durum Wheat Spaghetti", "Italian-style pasta.", inf, 119, 150f, "500 g", 30, img("1551462147-ff29053bfc14")),
                new Product("Paneer Butter Masala", "Ready to eat. Heat and serve.", inf, 149, 175f, "300 g", 25, img("1631452180519-c014fe946bc7")),
                new Product("Margherita Pizza (Frozen)", "Stone-baked, bake for 10 minutes.", inf, 199, 249f, "1 pc (300 g)", 15, img("1585238342024-78d387f4a707")),
                // Home & Personal Care
                new Product("Handmade Lavender Soap", "Cold-process soap with lavender oil.", hp, 199, 249f, "Pack of 3", 25, img("1600857544200-b2f666a9a2ec")),
                new Product("Toilet Tissue Rolls", "Soft 3-ply rolls.", hp, 149, 180f, "4 rolls", 40, img("1584556812952-905ffd0c611a")),
                new Product("Front Load Liquid Detergent", "Tough on stains, gentle on clothes.", hp, 199, 260f, "1 L", 30, img("1610557892470-55d9e80c0bce"))
            );
            // Store 2 sells drinks, snacks, sweets and frozen food; store 1 everything else
            java.util.Set<String> store2 = java.util.Set.of(dj, sm, si, inf);
            all.forEach(p -> p.setStoreId(store2.contains(p.getProductCategory()) ? 2 : 1));
            productRepository.saveAll(all);
        }
    }

}
