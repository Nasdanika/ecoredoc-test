package org.nasdanika.html.ecore.test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import org.apache.commons.codec.binary.Hex;
import org.eclipse.emf.codegen.ecore.genmodel.GenModel;
import org.eclipse.emf.codegen.ecore.genmodel.GenPackage;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.util.Diagnostician;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.json.JSONObject;
import org.jsoup.nodes.Element;
import org.nasdanika.common.ConsumerFactory;
import org.nasdanika.common.Context;
import org.nasdanika.common.Diagnostic;
import org.nasdanika.common.DiagnosticException;
import org.nasdanika.common.DiagramGenerator;
import org.nasdanika.common.ExecutionException;
import org.nasdanika.common.MutableContext;
import org.nasdanika.common.NasdanikaException;
import org.nasdanika.common.NullProgressMonitor;
import org.nasdanika.common.ProgressMonitor;
import org.nasdanika.common.Status;
import org.nasdanika.emf.EObjectAdaptable;
import org.nasdanika.exec.ExecPackage;
import org.nasdanika.exec.content.ContentPackage;
import org.nasdanika.exec.resources.Container;
import org.nasdanika.exec.resources.ResourcesFactory;
import org.nasdanika.exec.resources.ResourcesPackage;
import org.nasdanika.html.ecore.EcoreActionSupplier;
import org.nasdanika.html.ecore.EcoreActionSupplierAdapterFactory;
import org.nasdanika.html.ecore.GenModelResourceSet;
import org.nasdanika.html.model.app.Action;
import org.nasdanika.html.model.app.AppPackage;
import org.nasdanika.html.model.app.gen.AppAdapterFactory;
import org.nasdanika.html.model.app.gen.AppGenObjectLoaderSupplier;
import org.nasdanika.html.model.app.gen.Util;
import org.nasdanika.html.model.bootstrap.BootstrapPackage;
import org.nasdanika.html.model.html.HtmlPackage;
import org.nasdanika.resources.BinaryEntityContainer;
import org.nasdanika.resources.FileSystemContainer;

import com.redfin.sitemapgenerator.ChangeFreq;
import com.redfin.sitemapgenerator.WebSitemapGenerator;
import com.redfin.sitemapgenerator.WebSitemapUrl;

import net.sourceforge.plantuml.FileFormat;
import net.sourceforge.plantuml.FileFormatOption;
import net.sourceforge.plantuml.SourceStringReader;

public class EcoreDocGenerator {
	
	
	private static final URI CONTAINER_MODEL_URI = URI.createFileURI(new File("target/model-doc/container.xml").getAbsolutePath());				
	
	private DiagramGenerator createDiagramGenerator(ProgressMonitor progressMonitor) {
		DiagramGenerator plantUMLGenerator = new DiagramGenerator() {
			
			@Override
			public boolean isSupported(String dialect) {
				return DiagramGenerator.UML_DIALECT.equals(dialect);
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
	public void generateActionModel(Context ctx, ProgressMonitor progressMonitor) throws Exception {
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
		
		MutableContext context = ctx.fork();
		
		DiagramGenerator diagramGenerator = createDiagramGenerator(progressMonitor);
		context.register(DiagramGenerator.class, diagramGenerator);
		
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
	
	/**
	 * Generates a resource model from an action model.
	 * @throws Exception
	 */
	public void generateResourceModel(Context context, ProgressMonitor progressMonitor) throws Exception {
		Consumer<Diagnostic> diagnosticConsumer = diagnostic -> {
			if (diagnostic.getStatus() == Status.FAIL || diagnostic.getStatus() == Status.ERROR) {
				System.err.println("***********************");
				System.err.println("*      Diagnostic     *");
				System.err.println("***********************");
				diagnostic.dump(System.err, 4, Status.FAIL, Status.ERROR);
			}
			if (diagnostic.getStatus() !=Status.SUCCESS) {
				throw new DiagnosticException(diagnostic);
			};
		};
		
		String actionsResource = "actions.yml";
		Action root = (Action) Objects.requireNonNull(loadObject(actionsResource, diagnosticConsumer, context, progressMonitor), "Loaded null from " + actionsResource);
		
		Container container = ResourcesFactory.eINSTANCE.createContainer();
		container.setName("doc-site");
		
		ResourceSet resourceSet = new ResourceSetImpl();
		resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap().put(Resource.Factory.Registry.DEFAULT_EXTENSION, new XMIResourceFactoryImpl());
		Resource modelResource = resourceSet.createResource(CONTAINER_MODEL_URI);
		modelResource.getContents().add(container);
		
		String pageTemplateResource = "page-template.yml";
		org.nasdanika.html.model.bootstrap.Page pageTemplate = (org.nasdanika.html.model.bootstrap.Page) Objects.requireNonNull(loadObject(pageTemplateResource, diagnosticConsumer, context, progressMonitor), "Loaded null from " + pageTemplateResource);
		
		Util.generateSite(
				root, 
				pageTemplate,
				container,
				null,
				null,
				context,
				progressMonitor);
		
		modelResource.save(null);
	}
	
	/**
	 * Loads object from a classpath resource.
	 * @param resource
	 * @param diagnosticConsumer
	 * @param context
	 * @param progressMonitor
	 * @return
	 * @throws Exception
	 */
	private EObject loadObject(
			String resource, 
			Consumer<org.nasdanika.common.Diagnostic> diagnosticConsumer,
			Context context,
			ProgressMonitor progressMonitor) throws Exception {
		
		Class<?> clazz = EcoreDocGenerator.this.getClass();
		URL resourceURL = clazz.getResource(resource);
		if (resourceURL == null) {
			throw new IllegalArgumentException("Classloader resource not found: " + resource + " by " + clazz); 
		}
		URI resourceURI = URI.createURI(resourceURL.toString());
		
		// Diagnosing loaded resources. 
		try {
			return org.nasdanika.common.Util.call(new AppGenObjectLoaderSupplier(resourceURI, context), progressMonitor, diagnosticConsumer);
		} catch (DiagnosticException e) {
			System.err.println("******************************");
			System.err.println("*      Diagnostic failed     *");
			System.err.println("******************************");
			e.getDiagnostic().dump(System.err, 4, Status.FAIL);
			throw e;
		}		
	}
	
	/**
	 * Generates files from the previously generated resource model.
	 * @throws Exception
	 */
	public void generateContainer(Context context, ProgressMonitor progressMonitor) throws Exception {
		ResourceSet resourceSet = createResourceSet();
		
		resourceSet.getAdapterFactories().add(new AppAdapterFactory());
				
		Resource containerResource = resourceSet.getResource(CONTAINER_MODEL_URI, true);
	
		File siteDir = new File("target/model-doc/site");
		BinaryEntityContainer container = new FileSystemContainer(siteDir); 
		for (EObject eObject : containerResource.getContents()) {
			Diagnostician diagnostician = new Diagnostician();
			org.eclipse.emf.common.util.Diagnostic diagnostic = diagnostician.validate(eObject);
			if (diagnostic.getSeverity() == org.eclipse.emf.common.util.Diagnostic.ERROR) {
				throw new ExecutionException("Diagnostic failed");
			}
			try {
				ConsumerFactory<BinaryEntityContainer> consumerFactory = Objects.requireNonNull(EObjectAdaptable.adaptToConsumerFactory(eObject, BinaryEntityContainer.class), "Cannot adapt to ConsumerFactory");
				Diagnostic callDiagnostic = org.nasdanika.common.Util.call(consumerFactory.create(context), container, progressMonitor);
				Status status = callDiagnostic.getStatus();
				if (status == Status.FAIL || status == Status.ERROR) {
					System.err.println("******************************");
					System.err.println("*      Diagnostic error     *");
					System.err.println("******************************");
					callDiagnostic.dump(System.err, 4, Status.FAIL, Status.ERROR);				
				}
			} catch (DiagnosticException e) {
				System.err.println("******************************");
				System.err.println("*      Diagnostic failed     *");
				System.err.println("******************************");
				e.getDiagnostic().dump(System.err, 4, Status.FAIL);
				throw e;
			}
		}	
		
		generateSitemapAndSearch(new File(siteDir, "doc-site"));		
	}
	
	protected ResourceSet createResourceSet() {
		// Load model from XMI
		ResourceSet resourceSet = new ResourceSetImpl();
		resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap().put(Resource.Factory.Registry.DEFAULT_EXTENSION, new XMIResourceFactoryImpl());
	
		resourceSet.getPackageRegistry().put(ExecPackage.eNS_URI, ExecPackage.eINSTANCE);
		resourceSet.getPackageRegistry().put(ContentPackage.eNS_URI, ContentPackage.eINSTANCE);
		resourceSet.getPackageRegistry().put(ResourcesPackage.eNS_URI, ResourcesPackage.eINSTANCE);
		resourceSet.getPackageRegistry().put(HtmlPackage.eNS_URI, HtmlPackage.eINSTANCE);
		resourceSet.getPackageRegistry().put(BootstrapPackage.eNS_URI, BootstrapPackage.eINSTANCE);
		resourceSet.getPackageRegistry().put(AppPackage.eNS_URI, AppPackage.eINSTANCE);
		return resourceSet;
	}
	
	/**
	 * 
	 * @param siteDir
	 * @return Number of broken links.
	 * @throws IOException
	 */
	private void generateSitemapAndSearch(File siteDir) throws IOException {
		AtomicInteger problems = new AtomicInteger();
		
		// Site map and search index
		JSONObject searchDocuments = new JSONObject();		
		String domain = "https://docs.nasdanika.org";
		WebSitemapGenerator wsg = new WebSitemapGenerator(domain, siteDir);
		BiConsumer<File, String> listener = new BiConsumer<File, String>() {
			
			@Override
			public void accept(File file, String path) {
				if (path.endsWith(".html")) {
					try {
						WebSitemapUrl url = new WebSitemapUrl.Options(domain + "/" + path)
							    .lastMod(new Date(file.lastModified())).changeFreq(ChangeFreq.WEEKLY).build();
						wsg.addUrl(url); 
					} catch (MalformedURLException e) {
						throw new NasdanikaException(e);
					}
					
					// Excluding search.html and aggregator pages which contain information present elsewhere
					try {	
						Predicate<String> predicate = org.nasdanika.html.model.app.gen.Util.createRelativeLinkPredicate(file, siteDir);						
						Consumer<? super Element> inspector = org.nasdanika.html.model.app.gen.Util.createInspector(predicate, error -> {
							System.err.println("[" + path +"] " + error);
							problems.incrementAndGet();
						});
						JSONObject searchDocument = org.nasdanika.html.model.app.gen.Util.createSearchDocument(path, file, inspector, null);
						if (searchDocument != null) {
							searchDocuments.put(path, searchDocument);
						}
					} catch (IOException e) {
						throw new NasdanikaException(e);
					}
				}
			}
		};
		org.nasdanika.common.Util.walk(null, listener, siteDir.listFiles());
		wsg.write();	
	
		try (FileWriter writer = new FileWriter(new File(siteDir, "search-documents.js"))) {
			writer.write("var searchDocuments = " + searchDocuments);
		}
		
		if (problems.get() != 22) { // Assuming 22 known problems or false positives
			throw new ExecutionException("There are broken links: " + problems.get());
		};
	}
	
	/**
	 * Generates a resource model from an action model and then generates files from the resource model.
	 * @throws Exception
	 */
	public void generateSite() throws Exception {
		ProgressMonitor progressMonitor = new NullProgressMonitor();
		MutableContext context = Context.EMPTY_CONTEXT.fork();
		// Marker factory can be used to establish traceability from documentation to model sources in version control, e.g. using GitMarkerFactory.
		// context.register(MarkerFactory.class, ...);   
		generateActionModel(context, progressMonitor);
		generateResourceModel(context, progressMonitor);
		generateContainer(context, progressMonitor);
	}	

}
