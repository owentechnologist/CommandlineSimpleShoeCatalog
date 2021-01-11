package com.redislabs.sa.newbie;


public class Main {

    public static void main(String[] args) {
        // as we want to include several attributes
        // we should use a hash for the product
        // as we want to offer search by name and category
        // we will need to create indexes using
        // sorted and regular sets for the names and categories respectively
        // we will use zscan for the name search to allow for mid-string matching
    /*
        Product details should include a name, a description, a vendor, a price, a main category and some images
        It needs to be possible to create/update/delete product details in a safe way
        by taking the logical consistency of the catalog into account
        (e.g. a product can not belong to a category which is non-existent)
        Each object needs to be accessible via its unique key
        It needs to be possible to retrieve a product by searching by its name or a part of its name
        Products can be listed by their main category
     */
        ContextHolder cx = ContextHolder.getContextHolder();
        ProductCatalogImpl catalog = new ProductCatalogImpl();
        catalog.kickoff(args); // initialize properties, data, and connections
        cx.setProductCatalogue(catalog);
        View view = new CommandLineView();
        view.presentMessage("Welcome to the Product Catalog.");
        view.presentMessage("Please select from the following options: " +
                "\nType the number from one of the [ ] and hit enter");
        String answer = "";
        while (!cx.getState().equalsIgnoreCase(cx.ENDING)) {
            answer = (String) view.promptWithMessage(cx.getOptions(cx.getState()));
            cx.takeAction(answer);
        }
        //one last time before we go:
        answer = (String) view.promptWithMessage(cx.getOptions(cx.getState()));
        cx.takeAction(answer);
        System.exit(0);
    }
}