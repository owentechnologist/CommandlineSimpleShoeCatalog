package com.redislabs.sa.newbie;

import redis.clients.jedis.*;
import redis.clients.jedis.params.ZAddParams;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

public class ProductCatalogImpl implements ProductCatalogue{

    static String keysPrefix = "SAE1:";
    static String pName = null;
    static String allowedCategoriesKey = keysPrefix+"s:product:categories";
    static Properties props=null;
    static Jedis jedis = null;
    static String REDIS_HOST = "localhost";
    static Integer REDIS_PORT = 6379;
    static String REDIS_PASSWORD = "";
    static ContextHolder cx = ContextHolder.getContextHolder();
    static View view = new CommandLineView();
    static Set<String> categorySet = null;

    public void kickoff(String[] args){
        init(args);
        if(!dataReady()) {
            allowedCategoriesKey=keysPrefix+"s:product:categories";
            loadProductsFromPropsIntoRedis(props);
        }
        isGoodCategory("test");//This is just to init the Set of Categories
        Product.initFields();
        cx.setContextState(ContextHolder.NORMAL);
    }

    private boolean dataReady(){
        boolean result = false;
        Set<String> spn = jedis.keys(props.getProperty("keys_prefix")+"*");
        if(spn.size()>0){
            result=true;
        }
        return result;
    }

    private void init(String [] args){
        pName = "product.properties"; //default
        if(args.length>0) {
            String[] split1 = args[0].split("[.]");
            if (split1[1].equalsIgnoreCase("properties")) {
                pName = args[0];
            }else{
                view.presentMessage("The first argument is expected to be the name of a properties file");
                view.presentMessage("to use the default of product.properties - don't use any args" +
                        "\n this will mean all defaults are used for host, port, and password.\n" +
                        "This is all or nothing... if you pass in a properties file, you must also pass in\n" +
                        "args for host, port, and password - in that order.");
                throw new IllegalArgumentException(args[0]+"  is not a valid first argument.");
            }
            REDIS_HOST = args[1];
            REDIS_PORT = Integer.parseInt(args[2]);
            REDIS_PASSWORD = args[3];
        }
        props = loadProps(pName);
        jedis = getRedisConnection(REDIS_HOST,REDIS_PORT,REDIS_PASSWORD);
        keysPrefix=props.getProperty("keys_prefix");
    }

    private Jedis getRedisConnection(String rHost,Integer rPort,String rPassword){
        String REDIS_HOST = "localhost";
        Integer REDIS_PORT = 6379;
        String REDIS_PASSWORD = "";
        if(!(null==rHost)){REDIS_HOST = rHost;}
        if(!(null==rPort)){REDIS_PORT = rPort;}
        if(!(null==rPassword)){REDIS_PASSWORD = rPassword;}
        Jedis jedis = new Jedis(REDIS_HOST, REDIS_PORT);
        if (REDIS_PASSWORD.length() > 0) {
            jedis.auth(REDIS_PASSWORD);
        }
        return jedis;
    }

    private Properties loadProps(String propertyFileName){
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream(propertyFileName);
        Properties p = new Properties();
        try {
            p.load(inputStream);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return p;
    }

    @Override
    public Product[] getProductsByCategory(String category) {
        if(null==category){
            getProductsByCategory();
            return null;
        }
        Set<String> results = jedis.smembers(keysPrefix+"s:"+category);
        ArrayList<Product> pArr = new ArrayList<Product>();
        //build a Product instance for each returned member of the set:
        for(String catMembers:results){
            String prodID = catMembers.split("[:]")[1];
            Product p = new Product();
            //populate the fields of the product:
            for(String fieldName:Product.getFieldNames()) {
                String value = jedis.hget(keysPrefix+"product"+prodID,fieldName);
                p.setField(fieldName,value);
            }
            p.setId(Long.valueOf(prodID));
            pArr.add(p);
        }
        return (Product[])pArr.toArray();
    }

    @Override
    public void showProductImage(Product product) {
        Long prodID = null;
        cx.setUserResponse((String) view.promptWithMessage("Do you want to see the main image or one of the other images? M  or  O "));
        if(cx.getUserResponse().equalsIgnoreCase("m")){
            showMainProductImage(product);
        }else{
            if(null==product){
                cx.setUserResponse((String)view.promptWithMessage(
                        "\nTo fetch an image... enter the unique number for the product of interest   "));
                prodID = Long.valueOf(cx.getUserResponse());
            }else {
                prodID = product.getId();
            }
            String zKey = keysPrefix+"z:images:product:"+prodID;
            Long ct = jedis.zcard(zKey);
            view.presentMessage("there are "+ct+" images available for ID "+prodID);
            Double imageID = Double.valueOf((String)view.promptWithMessage("Please enter the number of the image you would like to see... (from 1 to "+ct+") "));
            Set<String> s =  jedis.zrangeByScore(zKey,imageID,imageID);
            byte[] image = Base64.getDecoder().decode((String)s.toArray()[0]);
            view.showImage(image);
        }
    }

    @Override
    public Product getProductByName(String productName) {
        boolean prodFound = false;
        if(null==productName){
            cx.setUserResponse((String)view.promptWithMessage("Enter the product Name (or part of it)  "));
            productName=cx.getUserResponse();
        }
        String cursor = ScanParams.SCAN_POINTER_START;
        ScanParams params = new ScanParams();
        params.match("*"+productName+"*");
        params.count(1);//has no effect on small set using SSCAN or ZSCAN
        view.presentMessage("Scanning for matches to: [*"+productName+"*]"+"  results in the following output:\n");
        ScanResult<Tuple> sr = null;
        do {
            sr = jedis.zscan(keysPrefix + "z:products",cursor, params);
            for(Tuple tup:sr.getResult()) {
                String keyName = tup.getElement();
                view.presentStringAsIs(keyName+"\t");
            }
            cx.setYN((String)view.promptWithMessage("Do you see your desired product? Y or N   "));
            if(cx.getYN().equalsIgnoreCase("y")){
                prodFound = true;
                break;
            }
            cursor = sr.getCursor();
        }while(!sr.isCompleteIteration());
        if(prodFound){
            offerProductDetail();
        }else{
            view.presentMessage("Check your capitalization and try again \n- or search by category.");
        }
            return null;
    }

    @Override
    public Product updateProduct(Product productToChange) {
        boolean needsCategorySwitch = false;
        boolean needsNameSwitch = false;
        String origName = null;
        String origCategory=null;
        if(null == productToChange) {
            cx.setUserResponse((String) view.promptWithMessage(
                    "\nEnter the unique number for an interesting product  "));
            cx.setCurrentProductHashKey(buildProductKeyFromId(cx.getUserResponse()));
            //below showProductByHKey assembles the Product object and adds it to cx
            showProductByHKey(cx.getCurrentProductHashKey());
        }else{
            cx.setLastLoadedProduct(productToChange);
            view.presentMessage(cx.getLastLoadedProduct().getStringForDisplayNoImage());
        }
        cx.setYN((String)view.promptWithMessage(
                "\nWould you like to update this product ? Y or N   "));
        if(cx.getYN().equalsIgnoreCase("y")) {
            do{
                cx.setUserResponse((String) view.promptWithMessage(
                        "Which of the following fields would you like to update?\n" + stringOfProductFields()));
                String fieldName = cx.getUserResponse();
                while ((!Product.isGoodFieldName(fieldName))||fieldName.equalsIgnoreCase("id")) {
                    cx.setUserResponse((String) view.promptWithMessage(
                            "Try Again please: (id cannot be changed) which of the following fields would you like to update?\n" + stringOfProductFields()));
                    fieldName = cx.getUserResponse();
                }
                cx.setUserResponse((String) view.promptWithMessage("What value do you want to assign to " + fieldName + "?  "));
                String fieldValue = cx.getUserResponse();
                if(fieldName.equalsIgnoreCase("category")) {
                    while(!isGoodCategory(fieldValue)){
                        view.presentMessage(fieldValue+" is not an allowed category...");
                        view.presentMessage("These are the allowed categories: "+stringOfProductCategories());
                        cx.setUserResponse((String) view.promptWithMessage("What value do you want to assign to " + fieldName + "?  "));
                         fieldValue = cx.getUserResponse();
                    }
                    if (isGoodCategory(fieldValue)) {
                        needsCategorySwitch = true;
                        origCategory = cx.getLastLoadedProduct().getCategory();
                    }
                }
                if(fieldName.equalsIgnoreCase("name")){
                    origName = cx.getLastLoadedProduct().getName();
                    needsNameSwitch = true;
                }
                cx.getLastLoadedProduct().setField(fieldName, fieldValue);
                cx.setYN((String)view.promptWithMessage("Would you like to update another field ?  Y  or N  "));
                if(cx.getYN().equalsIgnoreCase("y")){
                    cx.setWantMore(true);
                }else{cx.setWantMore(false);}
            }while(cx.isWantMore());
        }

        cx.getLastLoadedProduct().addMainImageAsByteArray(
                jedis.hget(cx.getCurrentProductHashKey().getBytes(),
                props.getProperty("image_field").getBytes()));

        Transaction t = jedis.multi();
        if(needsNameSwitch){
            //update all sets & sorted sets with new Name value
            //the hash is fine as the name is just a field within it
            String namOrigEntryValue = origName+":"+cx.getLastLoadedProduct().getId();
            String namEntryValue = cx.getLastLoadedProduct().getName()+":"+cx.getLastLoadedProduct().getId();
            if(!(null==origCategory)){
                t.srem(keysPrefix+"s:"+origCategory,namOrigEntryValue);
                t.sadd(keysPrefix+"s:"+origCategory,namEntryValue);
                t.zrem(keysPrefix+"z:products",namOrigEntryValue);
                t.zadd(keysPrefix+"z:products",0,namEntryValue);
            }else {
                t.srem(keysPrefix + "s:" + cx.getLastLoadedProduct().getCategory(), namOrigEntryValue);
                t.sadd(keysPrefix+"s:"+cx.getLastLoadedProduct().getCategory(),namEntryValue);
                t.zrem(keysPrefix+"z:products",namOrigEntryValue);
                t.zadd(keysPrefix+"z:products",0,namEntryValue);
            }
        }

        if(needsCategorySwitch){
            String catEntryValue = cx.getLastLoadedProduct().getName()+":"+cx.getLastLoadedProduct().getId();
            t.srem(keysPrefix+"s:"+origCategory,catEntryValue);
            t.sadd(keysPrefix+"s:"+cx.getLastLoadedProduct().getCategory(),catEntryValue);
        }
        t.hset(cx.getCurrentProductHashKey().getBytes(),cx.getLastLoadedProduct().getAttributes());
        t.exec();
        return cx.getLastLoadedProduct();
    }

    @Override
    public String addProduct(Product product) {
        addProductIntoCategory();
        return null;
    }

    @Override
    public String deleteProduct(Product product) {
        if(null==product) {
            cx.setUserResponse((String) view.promptWithMessage(
                    "\nEnter the unique number for an interesting product you wish to review...  "));
            cx.setCurrentProductHashKey(buildProductKeyFromId(cx.getUserResponse()));
            showProductByHKey(cx.getCurrentProductHashKey());
        }
        cx.setYN((String)view.promptWithMessage("\nWould you like to delete this particular product? Y or N   "));
        if(cx.getYN().equalsIgnoreCase("y")){
            view.presentMessage("Deleting ["+ cx.getCurrentProductHashKey()+"] and related index entries...");
            //there are two indexes that should contain a reference to this
            //product - as well as the hash entry for the entire product
            //hkey points to the hash entry
            //the other entries are built using the product name
            //just to be sure we can search for [prefix]:s and [prefix]:z
            //and delete the records from any matching sets found
            //for now - let's go easy way expect just 2 indexes/product
            //(that means products only get one category index in addition to their name being indexed)
            //both indexes use this formula to create their entries:
            //product.get("name")+":"+npid (where npid is the uniqueID of product)
            String prodName = jedis.hget(cx.getCurrentProductHashKey(),"name");
            String [] keyParts = cx.getCurrentProductHashKey().split("[:]");
            Long npid = Long.valueOf(keyParts[keyParts.length-1]);
            String productIndexesEntryValue = prodName+":"+npid;
            String categoryIndexKey = props.getProperty("keys_prefix")+"s:"+jedis.hget(cx.getCurrentProductHashKey(),"category");
            String productIndexKey = props.getProperty("keys_prefix")+"z:products";
            Transaction t = jedis.multi();
            t.unlink(cx.getCurrentProductHashKey());
            t.zrem(productIndexKey,productIndexesEntryValue);
            t.srem(categoryIndexKey,productIndexesEntryValue);
            t.exec();
        }
        view.presentMessage("product with key: "+cx.getCurrentProductHashKey()+" has been deleted.");
        return "product with key: "+cx.getCurrentProductHashKey()+" has been deleted.";
    }

    @Override
    public void cleanUp(){
        deleteKeysRelatedToThisExercise();
    }


    public void showMainProductImage(Product product) {
        if(null==product){
            cx.setUserResponse((String)view.promptWithMessage(
                    "\nEnter the unique number for the product you would like to see (it appears after the colon)   "));
            String hkey = keysPrefix+"product:"+cx.getUserResponse();
            showProductByHKey(hkey);
        }else {
            String prodHKey = buildProductKeyFromId(product.getId()+"");
            product.addMainImageAsByteArray(jedis.hget(prodHKey.getBytes(), props.getProperty("image_field").getBytes()));
            view.showImage(cx.getLastLoadedProduct().getMainImage());
        }
    }

    @Override
    public void addAdditionalImagesToProduct(Product product) {
        Long id = null;
        if(null == product){ // no product was passed in
            cx.setUserResponse((String)view.promptWithMessage("Please enter the id (number) of the product  "));
            if(!productAlreadyExists(cx.getUserResponse())) {
                view.presentMessage("Product with key ["+cx.getUserResponse()+"] does not exist - please add it or choose a different id.");
            }else{
                id = Long.valueOf(cx.getUserResponse());
            }
        }else{ //a Product was passed in
            id= product.getId();
        }
        cx.setYN((String)view.promptWithMessage(
                "If you have an image file available on your local machine " +
                "\nand would like to add it... type:  Y  "));
        if(cx.getYN().equalsIgnoreCase("y")){
            cx.setUserResponse((String)view.promptWithMessage("enter the path to the image file like this: /Users/Shared/images/tapShoes.jpeg  "));
            String imagePath = cx.getUserResponse();
            byte[] image = packImageFromFile(imagePath);
            //store the image in a sorted set named after the productID SAE1:z:images:product:1
            String imageSetKey = keysPrefix+"z:images:product:"+id;
            //encode the image as a 64 bit String:
            String imageAsString = new String(Base64.getEncoder().encode(image));
            // assign the image a score equal to its order of placement into the set
            Long score = jedis.zcard(imageSetKey); // returns 0 if nothing there
            //increment Score before setting it so the first entry has a score of [1]
            score++;
            Response<Long> outcome = null;
            redis.clients.jedis.params.ZAddParams p = new ZAddParams().nx();
            Transaction t = jedis.multi();
            outcome = t.zadd(imageSetKey,score,imageAsString,p);
            t.exec();
            if(outcome.get().longValue()<1){
                view.presentMessage("[NB:] You tried to add a duplicate image. The original is intact, and the duplicate has been ignored.");
            }
        }
    }

    private boolean productAlreadyExists(String id){
        boolean exists = false;
        exists = jedis.exists(buildProductKeyFromId(id));
        return exists;
    }

    private boolean isGoodCategory(String cat) {
        if(null == categorySet) {
            categorySet = jedis.smembers(allowedCategoriesKey);
        }
        if (categorySet.contains(cat)) {
            return true;
        } else {
            return false;
        }
    }

    private void addProductIntoCategory(){
        HashMap<String,String> productIngredients = new HashMap<String,String>();
        view.presentMessage("\n\nOur Products have the following basic fields: ");
        view.presentMessage(stringOfProductFields());
        view.presentMessage("\n\nWhen prompted for the product fields, please type your answer and hit enter...\n");
        for(String field:Product.getFieldNames()) {
            if(field.equalsIgnoreCase("category")){
                do{
                    view.presentMessage("[REMINDER]\n Our Products come in the following categories: ");
                    view.presentMessage(stringOfProductCategories()+"\n");
                    cx.setUserResponse((String)view.promptWithMessage("product "+field+":\t"));
                    productIngredients.put(field, cx.getUserResponse());
                }while(!isGoodCategory(cx.getUserResponse()));
            }else if(!field.equalsIgnoreCase("id")){ //id will be set independent of the user
                cx.setUserResponse((String) view.promptWithMessage("product " + field + ":\t"));
                productIngredients.put(field, cx.getUserResponse());
            }
        }
        cx.setYN((String)view.promptWithMessage("Optional: If you have an\nimage file available on your local machine " +
                "\nand would like to add it... type:  Y  "));
        if(cx.getYN().equalsIgnoreCase("y")){
            cx.setUserResponse((String)view.promptWithMessage("enter the path to the image file like this: /Users/Shared/images/tapShoes.jpeg  "));
            String imagePath = cx.getUserResponse();
            productIngredients.put("image_path", imagePath);
        }
        singleInsertProdHelper(productIngredients);
    }

    private byte[] packImageFromFile(String filePath) {
        //System.out.println("packImageFromFile passed: "+filePath);
        ByteArrayOutputStream outs=null;
        byte[] imagePayload = null;
        try{
            String[] imageParts = filePath.split("[.]");
            BufferedImage img = ImageIO.read(new File(filePath));
            outs = new ByteArrayOutputStream();
            ImageIO.write( img, imageParts[1], outs );
            outs.flush();
            imagePayload = outs.toByteArray();
        }catch(IOException e){
            System.out.println("yup: "+e.getMessage());
            e.printStackTrace();
        }finally{
            try {
                if (null != outs) {
                    outs.close();
                }
            }catch(java.io.IOException e){}//ignore
        }
        return imagePayload;
    }

    private void showProductByHKey(String hkey){
        view.presentMessage(buildProductFromKey(hkey).getStringForDisplayNoImage());
        cx.setYN((String)view.promptWithMessage("\nWould you like to see the main picture of this product? Y or N  "));
        if(cx.getYN().equalsIgnoreCase("y")) {
            cx.getLastLoadedProduct().addMainImageAsByteArray(jedis.hget(hkey.getBytes(),props.getProperty("image_field").getBytes()));
            if(!(null==cx.getLastLoadedProduct().getMainImage())) {
                view.showImage(cx.getLastLoadedProduct().getMainImage());
            }else{
                view.presentMessage("There is no image available for that Product.");
            }
        }
    }

    private void deleteKeysRelatedToThisExercise(){
        cx.setYN((String)view.promptWithMessage("Now's your chance to clean up!" +
                "\nAre you sure you want to delete all keys related to this project? Y or N   "));
        if(cx.getYN().equalsIgnoreCase("Y")){
            //delete all the keys created as part of this exercise
            Set<String> spn = jedis.keys(props.getProperty("keys_prefix")+"*");
            if(spn.size()>0) {
                for(String keyName:spn) {
                    jedis.unlink(keyName);
                }
            }
            view.presentMessage("KEYS BEGINNING WITH "+props.getProperty("keys_prefix")+" DELETED");
        }else {
            view.presentMessage("AS YOU REQUESTED: NO KEYS DELETED");
        }
    }

    public String stringOfProductCategories(){
        String out ="";
        Set<String> cats = jedis.smembers(allowedCategoriesKey);
        for(String cat:cats){
            out += cat+"\t";
        }
        return out;
    }

    public String stringOfProductFields(){
        String out ="";
        String[] fields=props.getProperty("fields").split("[:]");
        for(String field:fields){
            out += field+"\t";
        }
        return out;
    }

    private void singleInsertProdHelper(Map<String,String> productIngredients){
        Long npid = jedis.zcard(props.getProperty("keys_prefix")+"z:products");
        npid++;

        //build hashMap<byte[], byte[]> and add it as a product to redis:
        HashMap<byte[],byte[]> product = new HashMap<byte[],byte[]>();
        Set<String> ingredKeys = productIngredients.keySet();
        for(String s:ingredKeys) {
            if(s.equalsIgnoreCase("image_path")){
                product.put(("primary_image").getBytes(),packImageFromFile(productIngredients.get(s)));
            }else {
                product.put(s.getBytes(), productIngredients.get(s).getBytes());
            }
        }
        Transaction t = jedis.multi();
        t.hmset((buildProductKeyFromId(npid.toString())).getBytes(),product);
        //create index using sorted set where zero is used for score
        // and lexicographical values are [PRODUCT_NAME]:[PRODUCT_NUMBER]
        t.zadd(props.getProperty("keys_prefix")+"z:products",0,productIngredients.get("name")+":"+npid);
        //create additional indexes for each category of product
        //values will again be the name and product number separated by a colon [PRODUCT_NAME]:[PRODUCT_NUMBER]
        if ( isGoodCategory(productIngredients.get("category")) ){
            t.sadd(props.getProperty("keys_prefix")+"s:"+productIngredients.get("category"),productIngredients.get("name")+":"+npid);
        }else{
            throw new IllegalArgumentException("Somehow a bad category ["+productIngredients.get("category")+"] was entered!");
        }
        t.exec();
    }

    private Product buildProductFromKey(String key){
        String[] fields = props.getProperty("fields").split("[:]");
        Product p = new Product();
        if(jedis.exists(key)) {
            for(String field:fields){
                p.setField(field, jedis.hget(key, field));
            }
        }else{
            view.presentMessage("No Product matches key: "+key);
        }
        //this last bit is maybe redundant - but ensures the id stored matches the key:
        p.setId(Long.valueOf(key.split("[:]")[2])); //"SAE1:product:1" is a sample key
        cx.setLastLoadedProduct(p);
        return p;
    }

    private String buildProductKeyFromId(String id){
        return keysPrefix+"product:"+id;
    }

    private void getProductsByCategory(){
        view.presentMessage("Please type in one of these categories:   ");
        view.presentStringAsIs("all  \t");
        view.presentStringAsIs(stringOfProductCategories());
        cx.setUserResponse((String)view.promptWithMessage("\t"));
        view.presentMessage((String)"you entered: "+cx.getUserResponse());
        Set<String> catMembers = null;
        if(cx.getUserResponse().equalsIgnoreCase("all")){
            Set<String> bakedKeys = jedis.smembers(allowedCategoriesKey);
            for(String catKey:bakedKeys){
                catKey = keysPrefix+"s:"+catKey;
            }
//            for(int cc=0;cc<cats.length;cc++){
//                bakedKeys[cc] = props.getProperty("keys_prefix")+"s:"+cats[cc];
//            }
            catMembers = jedis.sunion((String[]) bakedKeys.toArray());
        }else {
            catMembers = jedis.smembers(keysPrefix+ "s:" + cx.getUserResponse());
        }
        view.presentMessage("I found: "+catMembers.size()+" matches!\n");
        for (String prod : catMembers){
            view.presentMessage("\t"+prod);
        }
        boolean moreDetails = false;
        do{
            offerProductDetail();
            cx.setYN((String)view.promptWithMessage("\nWould you like to get details for a different product? Y or N"));
            if(cx.getYN().equalsIgnoreCase("y")){
                moreDetails = true;
            }else{
                moreDetails=false;
            }
        } while(moreDetails);
    }

    private void offerProductDetail(){
            cx.setUserResponse((String)view.promptWithMessage("\nto see detail... Enter the unique number for the product of interest (it appears after the colon)   "));
            String hkey = keysPrefix+"product:"+cx.getUserResponse();
            showProductByHKey(hkey);
    }

    private void loadProductsFromPropsIntoRedis(Properties p){
        String[] fields = p.getProperty("fields").split("[:]");
        //System.out.println("fields.length == "+fields.length);
        Integer numOfProducts = Integer.valueOf(props.getProperty("numOfProducts"));
        while(numOfProducts>0){
            Transaction t = jedis.multi();
            String[] cats = props.getProperty("categories").split("[:]");
            for(String cat:cats){
                t.sadd(allowedCategoriesKey,cat);
            }
            Map<byte[],byte[]> m = new HashMap<byte[], byte[]>();
            for(int x=0; x < fields.length; x++){
                m.put((""+fields[x]).getBytes(),p.getProperty(numOfProducts+"."+fields[x]).getBytes());
            }
            m.put(("primary_image").getBytes(),packImageFromFile(props.getProperty(numOfProducts+".imagePath")));
            //System.out.println(("product:"+p.getProperty(numOfProducts+".name")));

            //add product as hash to redis:
            t.hmset((props.getProperty("keys_prefix")+"product:"+numOfProducts).getBytes(),m);

            //create index using sorted set where zero is used for score
            // and lexicographical values are [PRODUCT_NAME]:[PRODUCT_NUMBER]
            t.zadd(props.getProperty("keys_prefix")+"z:products",0,p.getProperty(numOfProducts+".name")+":"+numOfProducts);

            //create additional indexes for each category of product
            //values will again be the name and product number separated by a colon [PRODUCT_NAME]:[PRODUCT_NUMBER]
            String [] categories = p.getProperty("categories").split("[:]");
            for(int ct=0;ct<categories.length;ct++) {
                if (p.getProperty(numOfProducts + ".category").equalsIgnoreCase(categories[ct])){
                    t.sadd(props.getProperty("keys_prefix")+"s:"+categories[ct],p.getProperty(numOfProducts+".name")+":"+numOfProducts);
                }
            }
            numOfProducts--;
            t.exec();
        }
    }

}
