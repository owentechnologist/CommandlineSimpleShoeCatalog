package com.redislabs.sa.newbie;

public interface ProductCatalogue {

    public Product getProductByName(String productName);

    public String addProduct(Product product);

    public Product updateProduct(Product product);

    public String deleteProduct(Product product);

    public Product[] getProductsByCategory(String category);

    public void showProductImage(Product product);

    public void addAdditionalImagesToProduct(Product product);

    public void cleanUp();

}
