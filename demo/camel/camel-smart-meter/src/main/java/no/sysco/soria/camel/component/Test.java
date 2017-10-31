/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package no.sysco.soria.camel.component;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author aviveros
 */
public class Test {
    
    public static void main(String args[]) throws IOException{
        try {
            Test t = new Test();
            String x = t.readFile("/Users/aviveros/testEncoding.xml");
            x = x.replace("WINDOWS-1252", "UTF-8");
            
           // byte[] isoBytes = x.getBytes("ISO-8859-1");
            //System.out.println(new String(isoBytes, "UTF-8"));
            
            System.out.println(t.encodeNorsk(x));
            
            
        } catch (UnsupportedEncodingException ex) {
            Logger.getLogger(Test.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    private String readFile(String pathname) throws IOException {

    File file = new File(pathname);
    StringBuilder fileContents = new StringBuilder((int)file.length());
    Scanner scanner = new Scanner(file);
    String lineSeparator = System.getProperty("line.separator");

    try {
        while(scanner.hasNextLine()) {
            fileContents.append(scanner.nextLine() + lineSeparator);
        }
        return fileContents.toString();
    } finally {
        scanner.close();
    }
    
    }
    
    public String encodeNorsk(String str){
    //String value = "á, é, í, ó, ú, ü, ñ, ¿";
          String retValue = "";
          String convertValue2 = "";
          ByteBuffer convertedBytes = null;
          try {

              CharsetEncoder encoder2 = Charset.forName("Windows-1252").newEncoder();
              CharsetEncoder encoder3 = Charset.forName("UTF-8").newEncoder();           
              System.out.println("value = " + str);

              assert encoder2.canEncode(str);
              assert encoder3.canEncode(str);

              ByteBuffer conv1Bytes = encoder2.encode(CharBuffer.wrap(str.toCharArray()));

              retValue = new String(conv1Bytes.array(), Charset.forName("Windows-1252"));

              System.out.println("retValue = " + retValue);

              convertedBytes = encoder3.encode(CharBuffer.wrap(retValue.toCharArray()));
              convertValue2 = new String(convertedBytes.array(), Charset.forName("UTF-8"));
              System.out.println("convertedValue =" + convertValue2);

          } catch (Exception e) {
              e.printStackTrace();
          }
        return retValue;

        }
    
}
