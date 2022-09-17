package org.nasdanika.html.ecore.tests;

import org.junit.Test;
import org.nasdanika.html.ecore.test.EcoreDocGenerator;


public class TestEcoreDoc {
		
	@Test 
	public void generateSite() throws Exception {
		new EcoreDocGenerator().generateSite();
	}
	
}
