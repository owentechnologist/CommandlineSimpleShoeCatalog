package com.redislabs.sa.newbie;

public interface View {

    public Object promptWithMessage(Object message);

    public void presentMessage(Object message);

    public void presentStringAsIs(String s);

    public Object getInput();

    public void showImage(Object image);

}
