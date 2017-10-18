/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package no.sysco.soria.camel.component;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author aviveros
 */
public class OAGProcessor implements Processor {

    private static Logger LOG = LoggerFactory.getLogger(OAGProcessor.class);
    
    @Override
    public void process(Exchange msg) throws Exception {
        String text = msg.getIn().getBody(String.class);
        LOG.info("Message received: " + text);    
        msg.getOut().setBody(text);
    }   
    
}