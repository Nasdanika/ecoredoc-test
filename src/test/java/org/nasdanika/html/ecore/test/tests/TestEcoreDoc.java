package org.nasdanika.html.ecore.test.tests;

import org.junit.jupiter.api.Test;
import org.nasdanika.html.ecore.test.EcoreDocGenerator;


public class TestEcoreDoc {
		
	@Test 
	public void generateSite() throws Exception {
		new EcoreDocGenerator().generateSite();
	}
	
}
