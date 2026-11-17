package com.brandex.service;

import java.util.LinkedList;
import java.util.TreeMap;
import java.util.function.Consumer;

import com.brandex.models.Product;
import com.brandex.repository.ProductRepository;

// The service class for managing products.
public class ProductService {
    private static ProductService instance;
    private final TreeMap<String, Product> productTree = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    private final ProductRepository productRepo = ProductRepository.getInstance();
    private String searchQuery = "";
    private boolean loaded = false;

    // Returns the instance of the ProductService
    public static ProductService getInstance() {
        if (instance == null)
            instance = new ProductService();
        return instance;
    }

    // Load all products from database
    public void loadProducts() {
        this.productRepo.listProducts().forEach(product -> this.productTree.put(product.getName(), product));
        this.loaded = true;
    }

    // Reloads the products
    public void reloadProducts() {
        this.productTree.clear();
        this.loadProducts();
    }

    // Returns true if the products are loaded
    public boolean isLoaded() {
        return loaded;
    }

    // Iterates over all products
    public void forEachProduct(Consumer<Product> action) {
        this.productTree.values().forEach(action);
    }

    // Creates a new product
    public void createProduct(Product product) throws Exception {
        if (product == null)
            throw new Exception("Product cannot be null.");

        String generatedId = this.productRepo.createProduct(product);
        if (generatedId == null)
            throw new Exception("Failed to save product to database.");

        product.setId(generatedId);
        this.productTree.put(product.getName(), product);
    }

    // Updates a product
    public void updateProduct(Product product, String oldName, Runnable updateAction) throws Exception {
        if (product == null)
            throw new Exception("Product cannot be null.");

        // remove from tree using the old key before it's modified
        this.productTree.remove(oldName);

        // apply the updates (name, etc.)
        updateAction.run();

        // update database
        boolean ok = this.productRepo.updateProduct(product);
        if (!ok) {
            // Re-insert if DB fails (using the name it has now)
            this.productTree.put(product.getName(), product);
            throw new Exception("Failed to update product in database.");
        }

        // re-insert with the new key position
        this.productTree.put(product.getName(), product);
    }

    // Deletes a product
    public void deleteProduct(Product product) throws Exception {
        if (product == null)
            throw new Exception("Product cannot be null.");

        boolean ok = this.productRepo.deleteProduct(product.getId());
        if (!ok)
            throw new Exception("Failed to delete product from database.");

        this.productTree.remove(product.getName());
    }

    // Searches for products by keyword
    public LinkedList<Product> searchByKeyword(String keyword) {
        if (keyword == null)
            throw new IllegalArgumentException("Keyword cannot be null.");
        if (keyword.isEmpty())
            keyword = this.searchQuery;

        LinkedList<Product> results = new LinkedList<>();
        String lowerKeyword = keyword.toLowerCase();

        this.productTree.values().forEach(p -> {
            if (p.getName().toLowerCase().contains(lowerKeyword) || p.getCategory().toLowerCase().contains(lowerKeyword)
                    || p.getBrand().toLowerCase().contains(lowerKeyword)) {
                results.add(p);
            }
        });

        return results;
    }

    // Searches for a product by ID
    public Product searchById(String id) {
        final Product[] found = new Product[1];
        this.productTree.values().forEach(p -> {
            if (p.getId().equals(id)) {
                found[0] = p;
            }
        });
        return found[0];
    }

    // Returns the search query
    public String getSearchQuery() {
        return this.searchQuery;
    }

    // Sets the search query
    public void setSearchQuery(String q) {
        this.searchQuery = q;
    }

    // Clears the products
    public void clearProducts() {
        this.productTree.clear();
        this.loaded = false;
    }

    // Returns the products tree
    public TreeMap<String, Product> getProductsTree() {
        return productTree;
    }
}
