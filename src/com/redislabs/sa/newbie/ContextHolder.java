package com.redislabs.sa.newbie;

import java.util.*;

public class ContextHolder {

    public static final String NEW = "new";
    public static final String NORMAL = "normal";
    public static final String ENDING = "ending";
    public static final String CURRENT_PRODUCT_HASH_KEY = "hkey";
    private static boolean wantMore = true;
    private static ArrayList<String> ctxStates = new ArrayList<String>();
    private static String contextState = NEW;
    private static HashMap<String,String> context = new HashMap<String,String>();
    private static String userResponse = "";
    private static String yn = "";
    private static Product lastLoadedProduct=null;
    private ProductCatalogue catalogueImpl = null;
    private static ContextHolder cxHolder = new ContextHolder();

    public static ContextHolder getContextHolder(){
        return cxHolder;
    }

    public void setProductCatalogue(ProductCatalogue val){
        this.catalogueImpl = val;
    }

    public void setLastLoadedProduct(Product p){
        lastLoadedProduct = p;
    }
    public Product getLastLoadedProduct(){
        return lastLoadedProduct;
    }

    public String getYN(){
        return yn;
    }
    public void setYN(String val){
        if(val.equalsIgnoreCase("Y")||val.equalsIgnoreCase("N")) {
            yn = val;
        }else{
            throw new IllegalArgumentException("Please just use Y or N");
        }
    }

    public String getUserResponse(){
        return userResponse;
    }

    public void setUserResponse(String s){
        userResponse = s;
    }

    public ContextHolder(){
      initContext();
    }

    public void setWantMore(boolean val){
        this.wantMore = val;
    }

    public boolean isWantMore(){
        return this.wantMore;
    }

    public void initContext(){
        ctxStates.add(NEW);
        ctxStates.add(NORMAL);
        ctxStates.add(ENDING);
        context.put(NEW,"\n[1] Add Product\nLoad Data [2]\nExit [3]");
        context.put(NORMAL,"\n[1] Add Product\n[2] Update Product \n[3] Get Product By Name \n[4] Get Products by Category\n[5] Display Product Image" +
                "\n[6] Add additional images to a product listing \n[7] Delete Product\n[8] Exit ");
        context.put(ENDING,"\n[1] Remove all related data and exit \n[2] Just Exit ");
    }

    public String getState(){
        return this.contextState;
    }

    public void setContextState(String val){
        if( ctxStates.contains(val)) {
            this.contextState = val;
        }else{
            throw new IllegalArgumentException(val+" is not a recognized option...");
        }
    }

    public void setCurrentProductHashKey(String hkey){
        context.put(CURRENT_PRODUCT_HASH_KEY,hkey);
    }

    public String getCurrentProductHashKey(){
        return context.get(CURRENT_PRODUCT_HASH_KEY);
    }

    public String getOptions(String val){
        //val is the contextState
        if(null == val){
            val = getState();
        }
        return this.context.get(val);
    }

    public boolean takeAction(String userResponse){
        switch(getState()) {
            case NEW:
                switch(userResponse){
                    // this state is immediately changed to NORMAL so below options never get executed
                    case "1":
                        //do stuff; add product
                        break;
                    case "2":
                        //do stuff: load data
                        break;
                    case "3":
                        //do stuff: Exit
                        setContextState(ENDING);
                        break;
                    case "default":
                        //prompt for correct user input
                        //falls through and exits switch
                }
            case NORMAL:
                switch(userResponse){
                    case "1":
                        //do stuff; add product
                        catalogueImpl.addProduct(null);
                        break;
                    case "2":
                        //do stuff: update product
                        catalogueImpl.updateProduct(null);
                        break;
                    case "3":
                        //do stuff: get product by name
                        catalogueImpl.getProductByName(null);
                        break;
                    case "4":
                        //do stuff: get products by category
                        catalogueImpl.getProductsByCategory(null);
                        break;
                    case "5":
                        //do stuff: display product image
                        catalogueImpl.showProductImage(null);
                        break;
                    case "6":
                        //do stuff: add additional Images to product
                        catalogueImpl.addAdditionalImagesToProduct(null);
                        break;
                    case "7":
                        //do stuff: delete product
                        catalogueImpl.deleteProduct(null);
                        break;
                    case "8":
                        //do stuff: exit
                        this.setContextState(ENDING);
                        break;
                    case "default":
                        //prompt for correct user input
                        //falls through and exits switch
                }
            case ENDING:
                switch(userResponse) {
                    case "1":
                        //do stuff; cleanup and exit
                        catalogueImpl.cleanUp();
                        break;
                    case "2":
                        //do stuff:  just exit
                        break;
                    case "default":
                        //prompt for correct user input
                        //falls through and exits switch
                }
        }
        return isWantMore();
    }

}
