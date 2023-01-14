package org.nasdanika.html.ecore.test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import org.apache.commons.codec.binary.Hex;
import org.eclipse.emf.codegen.ecore.genmodel.GenModel;
import org.eclipse.emf.codegen.ecore.genmodel.GenPackage;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.nasdanika.common.Context;
import org.nasdanika.common.DiagramGenerator;
import org.nasdanika.common.MutableContext;
import org.nasdanika.common.NullProgressMonitor;
import org.nasdanika.common.ProgressMonitor;
import org.nasdanika.emf.EObjectAdaptable;
import org.nasdanika.html.ecore.EcoreActionSupplier;
import org.nasdanika.html.ecore.EcoreActionSupplierAdapterFactory;
import org.nasdanika.html.ecore.GenModelResourceSet;
import org.nasdanika.html.model.app.gen.ActionSiteGenerator;

import net.sourceforge.plantuml.FileFormat;
import net.sourceforge.plantuml.FileFormatOption;
import net.sourceforge.plantuml.SourceStringReader;

public class EcoreDocGenerator {
		
	private DiagramGenerator createDiagramGenerator(ProgressMonitor progressMonitor) {
		DiagramGenerator plantUMLGenerator = new DiagramGenerator() {
			
			@Override
			public boolean isSupported(String dialect) {
				return DiagramGenerator.UML_DIALECT.equals(dialect)
						|| DiagramGenerator.GANTT_DIALECT.equals(dialect)
						|| DiagramGenerator.MINDMAP_DIALECT.equals(dialect)
						|| DiagramGenerator.SALT_DIALECT.equals(dialect)
						|| DiagramGenerator.WBS_DIALECT.equals(dialect);
			}
			
			@Override
			public String generateDiagram(String spec, String dialect) {
				try {
					ByteArrayOutputStream baos = new ByteArrayOutputStream();
					StringBuilder sb = new StringBuilder("@start")
							.append(dialect)
							.append(System.lineSeparator())
							.append(spec)
							.append(System.lineSeparator())
							.append("@end")
							.append(dialect)
							.append(System.lineSeparator());
					
					SourceStringReader reader = new SourceStringReader(sb.toString());
					
					FileFormatOption fileFormatOption = new FileFormatOption(FileFormat.PNG);
					reader.outputImage(baos, 0, fileFormatOption);		
					String diagramCMap = reader.getCMapData(0, fileFormatOption);
					baos.close();
	
					StringBuilder ret = new StringBuilder("<img src=\"data:image/png;base64, ");
					ret
						.append(Base64.getEncoder().encodeToString(baos.toByteArray()))
						.append("\"");
					
					if (org.nasdanika.common.Util.isBlank(diagramCMap)) {
						ret.append("/>");
						return ret.toString();			
					}
					
					String openingTag = "<map id=\"plantuml_map\" name=\"plantuml_map\">";
					if (diagramCMap.startsWith(openingTag)) {
						String mapId = "plantuml_map_" + UUID.randomUUID().toString();
						ret			
						.append(" usemap=\"#")
						.append(mapId)
						.append("\"/>")
						.append(System.lineSeparator())
						.append("<map id=\"")
						.append(mapId)
						.append("\" name=\"")
						.append(mapId)
						.append("\">")
						.append(diagramCMap.substring(openingTag.length()));
						
					} else {				
						ret			
							.append(" usemap=\"#plant_uml_map\"/>")
							.append(System.lineSeparator())
							.append(diagramCMap);
						return ret.toString();
					}
											
					return ret.toString();			
				} catch (Exception e) {
					return "<div class=\"nsd-error\">Error during diagram rendering: " + e + "</div>";
				}
			}
		};
		return DiagramGenerator.INSTANCE.compose(plantUMLGenerator); //.cachingDiagramGenerator(output.stateAdapter().adapt(decoder, encoder), progressMonitor);
	}
		
	/**
	 * Generating action models from Ecore models.
	 * @param ctx
	 * @param progressMonitor
	 * @throws Exception
	 */
	public void generateActionModel(Context context, ProgressMonitor progressMonitor) throws Exception {
		GenModelResourceSet ecoreModelsResourceSet = new GenModelResourceSet();
		
		Map<String,String> pathMap = new ConcurrentHashMap<>();
		
		Function<EPackage,String> getEPackagePath = ePackage -> {
			for (int i = 0; i < Integer.MAX_VALUE; ++i) {
				String path = i == 0 ? ePackage.getName() : ePackage.getName() + "_" + i;
				if (pathMap.containsKey(path)) {
					if (ePackage.getNsURI().equals(pathMap.get(path))) {
						return path;
					}
				} else {
					pathMap.put(path, ePackage.getNsURI());
					return path;
				}
			}
			
			// Encoding NS URI as HEX. Shall never reach this point.
			return Hex.encodeHexString(ePackage.getNsURI().getBytes(StandardCharsets.UTF_8));
		};
		
		ecoreModelsResourceSet.getAdapterFactories().add(new EcoreActionSupplierAdapterFactory(context, getEPackagePath, org.nasdanika.common.Util.createNasdanikaJavadocResolver(new File("../.."), progressMonitor)) {
			
//			@Override
//			protected boolean isDocumentable(EModelElement modelElement) {
//				if (modelElement instanceof ENamedElement) {
//					return !"target".equals(((ENamedElement) modelElement).getName()); 
//				}
//				return super.isDocumentable(modelElement);
//			} 
//			
//			@Override
//			protected EReference getEClassifierRole(EClassifier eClassifier) {
//				if (eClassifier.getName().equals("Note")) {
//					return AppPackage.Literals.ACTION__ANONYMOUS;
//				}
//				return super.getEClassifierRole(eClassifier);
//			}
			
			@Override
			protected String getDiagramDialect() {
				return DiagramGenerator.UML_DIALECT;
			}
			
//			/**
//			 * Built-in resolution does not work in Java 11.
//			 */
//			@Override
//			protected Object getEPackage(String nsURI) {
//				switch (nsURI) {
//				case DiagramPackage.eNS_URI:
//					return DiagramPackage.eINSTANCE;
//				case ExecPackage.eNS_URI:
//					return ExecPackage.eINSTANCE;
//				case ContentPackage.eNS_URI:
//					return ContentPackage.eINSTANCE;
//				case ResourcesPackage.eNS_URI:
//					return ResourcesPackage.eINSTANCE;
//				case FlowPackage.eNS_URI:
//					return FlowPackage.eINSTANCE;
//				case NcorePackage.eNS_URI:
//					return NcorePackage.eINSTANCE;
//				
//				case HtmlPackage.eNS_URI:
//					return HtmlPackage.eINSTANCE;
//				case BootstrapPackage.eNS_URI:
//					return BootstrapPackage.eINSTANCE;
//				case AppPackage.eNS_URI:
//					return AppPackage.eINSTANCE;
//				
//				case EngineeringPackage.eNS_URI:
//					return EngineeringPackage.eINSTANCE;
//				case JourneyPackage.eNS_URI:
//					return JourneyPackage.eINSTANCE;
//				default:
//					return super.getEPackage(nsURI);
//				}
//			}
						
		});
		
		// Physical location relative to the projects (git) root folder -> logical (workspace) name 
		Map<String,String> bundleMap = new LinkedHashMap<>();
		bundleMap.put("core/ncore", "org.nasdanika.ncore");
		bundleMap.put("core/exec", "org.nasdanika.exec");
		bundleMap.put("core/flow", "org.nasdanika.flow");
	
		File modelDir = new File("target/models").getAbsoluteFile();
		modelDir.mkdirs();
		
		File modelDocActionsDir = new File("target/model-doc/actions").getAbsoluteFile();
		org.nasdanika.common.Util.delete(modelDocActionsDir);
		modelDocActionsDir.mkdirs();
		
		Map<URI,File> modelToActionModelMap = new LinkedHashMap<>();
		
		File projectsRoot = new File("..").getCanonicalFile();		
		for (Entry<String, String> be: bundleMap.entrySet()) {					
			File sourceDir = new File(projectsRoot, be.getKey());
			File targetDir = new File(modelDir, be.getValue());
			org.nasdanika.common.Util.copy(new File(sourceDir, "model"), new File(targetDir, "model"), true, (source, target) -> {
				if (target.getName().endsWith(".genmodel")) {
					modelToActionModelMap.put(URI.createFileURI(target.getAbsolutePath()), new File(modelDocActionsDir, target.getName() + ".xml"));
				}
			});			
			org.nasdanika.common.Util.copy(new File(sourceDir, "doc"), new File(targetDir, "doc"), true, null);
		}
		
	//	URL eCoreGenmodelURL = getClass().getResource("/model/Ecore.genmodel");
	//	URI eCoreGenmodelURI = URI.createURI(eCoreGenmodelURL.toString());
	//	ecoreModelsResourceSet.getResource(eCoreGenmodelURI, true);
		
		// Loading resources to the resource set.
		for (URI uri: modelToActionModelMap.keySet()) {
			ecoreModelsResourceSet.getResource(uri, true);
		}		
		
		EcoreUtil.resolveAll(ecoreModelsResourceSet);
		
		ResourceSet actionModelsResourceSet = new ResourceSetImpl();
		actionModelsResourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap().put(Resource.Factory.Registry.DEFAULT_EXTENSION, new XMIResourceFactoryImpl());
		
		// Generating
		for (URI uri: modelToActionModelMap.keySet()) {
			Resource ecoreModelResource = ecoreModelsResourceSet.getResource(uri, false);
			File output = modelToActionModelMap.get(ecoreModelResource.getURI());
			
			Resource actionModelResource = actionModelsResourceSet.createResource(URI.createFileURI(output.getAbsolutePath()));
			
			for (EObject contents: ecoreModelResource.getContents()) {
				if (contents instanceof GenModel) {
					for (GenPackage genPackage: ((GenModel) contents).getGenPackages()) {
						EPackage ecorePackage = genPackage.getEcorePackage();
						actionModelResource.getContents().add(EObjectAdaptable.adaptTo(ecorePackage, EcoreActionSupplier.class).execute(null, progressMonitor));
					}
				}
			}
	
			actionModelResource.save(null);
		}		
	}
	
	private static URI getResourceURI(String path) {
		Class<?> clazz = EcoreDocGenerator.class;
		URL resourceURL = clazz.getResource(path);
		if (resourceURL == null) {
			throw new IllegalArgumentException("Classloader resource not found: " + path + " by " + clazz); 
		}
		return URI.createURI(resourceURL.toString());		
	}
	
	/**
	 * Generates a resource model from an action model and then generates files from the resource model.
	 * @throws Exception
	 */
	public void generateSite() throws Exception {
		ProgressMonitor progressMonitor = new NullProgressMonitor();
		MutableContext context = Context.EMPTY_CONTEXT.fork();
		DiagramGenerator diagramGenerator = createDiagramGenerator(progressMonitor);
		context.register(DiagramGenerator.class, diagramGenerator);
		
		// Marker factory can be used to establish traceability from documentation to model sources in version control, e.g. using GitMarkerFactory.
		// context.register(MarkerFactory.class, ...);   
		generateActionModel(context, progressMonitor);
		ActionSiteGenerator siteGenerator = new ActionSiteGenerator() {
			
			@Override
			protected MutableContext createContext(ProgressMonitor progressMonitor) {
				return context;
			}
			
		};
		
		Map<String, Collection<String>> errors = siteGenerator.generate(
				getResourceURI("actions.yml"),
				getResourceURI("page-template.yml"),
				"https://nasdanika.org/test",
				new File("target/ecoredoc-site"),
				new File("target/ecoredoc-work-dir"),
				false);
		
		System.out.println(errors);
	}	

}
