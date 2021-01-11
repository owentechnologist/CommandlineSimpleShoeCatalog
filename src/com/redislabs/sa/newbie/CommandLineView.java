package com.redislabs.sa.newbie;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Scanner;

public class CommandLineView implements View{
    Scanner in = new Scanner(System.in);
    String s = "";

    @Override
    public Object getInput() {
        s=in.nextLine();
        return s;
    }

    @Override
    public Object promptWithMessage(Object message) {
        System.out.println(message);
        s=in.nextLine();
        return s;
    }

    @Override
    public void presentMessage(Object message) {
        System.out.println(message);
    }

    @Override
    public void presentStringAsIs(String s) {
        System.out.print(s);
    }

    @Override
    public void showImage(Object image) {
        // expects byte ARRAY which will be passed to
        //byteArrayToImage()
        //which will in turn provide a BufferedImage to showBufferedImage
        if(!(null==image)) {
            showBufferedImage(byteArrayToImage((byte[]) image));
        }
    }

    private void showBufferedImage(BufferedImage img){
        JFrame frame = new JFrame();
        frame.getContentPane().setLayout(new FlowLayout());
        frame.getContentPane().add(new JLabel(new ImageIcon(img)));
        frame.pack();
        frame.setVisible(true);
    }

    private  BufferedImage  byteArrayToImage(byte[] bytes){
        BufferedImage bufferedImage=null;
        try {
            InputStream inputStream = new ByteArrayInputStream(bytes);
            bufferedImage = ImageIO.read(inputStream);
        } catch (IOException ex) {
            System.out.println(ex.getMessage());
        }
        return bufferedImage;
    }


}
