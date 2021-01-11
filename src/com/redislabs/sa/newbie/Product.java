package com.redislabs.sa.newbie;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;

public class Product {
    private String name,description,vendor,currency,category,imagePath;
    private Long id;
    private Double price;
    private byte[] mainImage;
    private static ArrayList<String> fieldNames = new ArrayList<String>();

    static void initFields(){
        fieldNames.add("name");
        fieldNames.add("description");
        fieldNames.add("vendor");
        fieldNames.add("price");
        fieldNames.add("currency");
        fieldNames.add("category");
        fieldNames.add("id");
    }

    public static ArrayList<String> getFieldNames(){
        return fieldNames;
    }

    public void addMainImageAsByteArray(byte[] val){
        this.mainImage=val;
    }

    public void setImagePath(String val){
        this.imagePath = val;
    }

    public String getImagePath(){
        return this.imagePath;
    }

    public String getCategory(){
        return this.category;
    }

    public void setCategory(String val){
        this.category = val;
    }

    public static boolean isGoodFieldName(String val){
        boolean b = false;
        if(fieldNames.contains(val)){
           b=true;
        }
        //System.out.println("DEBUG: Product>isGoodFieldName("+val+") returning: "+b);
        return b;
    }

    public HashMap<byte[],byte[]> getAttributes(){
        HashMap<byte[],byte[]> byteMap = new HashMap<byte[],byte[]>();
        byteMap.put("id".getBytes(),this.id.toString().getBytes());
        byteMap.put("name".getBytes(),this.name.getBytes());
        byteMap.put("description".getBytes(),this.description.getBytes());
        byteMap.put("vendor".getBytes(),this.vendor.getBytes());
        byteMap.put("price".getBytes(),this.price.toString().getBytes());
        byteMap.put("currency".getBytes(),this.currency.getBytes());
        byteMap.put("category".getBytes(),this.category.getBytes());
        byteMap.put("main_image".getBytes(),this.mainImage);
        return byteMap;
    }

    public String getStringForDisplayNoImage() {
        String response = "\nProduct ID: " + this.id;
        response += "\nName: " + this.name;
        response += "\nDescription: " + this.description;
        response += "\nVendor: " + this.vendor;
        response += "\nPrice: " + this.price;
        response += "\nCurrency: " + this.currency;
        response += "\nCategory: " + this.category;
        return response;
    }

    public byte[] getMainImage(){
        return this.mainImage;
    }

    public void setName(String val){
        this.name = val;
    }

    public void setDescription(String val){
        this.description = val;
    }

    public void setVendor(String val){
        this.vendor = val;
    }

    public Double getPrice(){
        return this.price;
    }

    public void setPrice(String val) throws IllegalArgumentException{
        try{
            Double d =  Double.parseDouble(val); //does this test pass?
            this.price = d;
        }catch(NullPointerException npe){
            System.out.println(npe.getMessage());
            throw new IllegalArgumentException("Oops!   looking for an item price. You passed in "+val);
        }catch (NumberFormatException nfe){
            System.out.println(nfe.getMessage());
            throw new IllegalArgumentException("Oops!   looking for an item price. You passed in "+val);
        }
    }

    public void setCurrency(String val){
        this.currency = val;
    }

    public void setId(Long val){
        this.id= val;
    }

    public Long getId(){
        return this.id;
    }

    public String getName(){
        return this.name;
    }

    public void setField(String fieldN, String val){
        //name,description,vendor,price,currency,category,imagePath
        //imagePath and id are set in a separate and deliberate manner
        if(isGoodFieldName(fieldN)) {
            switch (fieldN) {
                case "name":
                    setName((String)val);
                    break;
                case "description":
                    setDescription((String)val);
                    break;
                case "vendor":
                    setVendor((String)val);
                    break;
                case "price":
                    setPrice((String)val);
                    break;
                case "currency":
                    setCurrency((String)val);
                    break;
                case "category":
                    setCategory((String)val);
                    break;
                case "default":
                    //prompt for correct user input
                    //falls through and exits switch
                    //silent fail also an option...
            }
        }else{
            throw new IllegalArgumentException("Bad field: ["+fieldN+"] does not exist.");
        }

    }

}