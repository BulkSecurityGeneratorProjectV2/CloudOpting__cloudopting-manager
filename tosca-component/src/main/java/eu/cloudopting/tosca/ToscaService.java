package eu.cloudopting.tosca;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;

import javax.swing.text.AbstractDocument.Content;
import javax.xml.bind.JAXBException;
import javax.xml.namespace.QName;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.xalan.extensions.XPathFunctionResolverImpl;
import org.apache.xerces.dom.DocumentImpl;
import org.apache.xerces.jaxp.DocumentBuilderFactoryImpl;
import org.apache.xerces.jaxp.DocumentBuilderImpl;

import org.apache.xerces.xs.XSModel;
import org.apache.xml.dtm.ref.DTMNodeList;
import org.apache.xpath.jaxp.XPathFactoryImpl;
import org.apache.xpath.jaxp.XPathImpl;
import org.eclipse.persistence.dynamic.DynamicEntity;
import org.eclipse.persistence.dynamic.DynamicType;
import org.eclipse.persistence.exceptions.DynamicException;
import org.eclipse.persistence.internal.oxm.Marshaller;
import org.eclipse.persistence.jaxb.JAXBMarshaller;
import org.eclipse.persistence.jaxb.dynamic.DynamicJAXBContext;
import org.eclipse.persistence.jaxb.dynamic.DynamicJAXBContextFactory;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.traverse.TopologicalOrderIterator;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.w3c.dom.DOMException;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import eu.cloudopting.exception.ToscaException;
import eu.cloudopting.tosca.utils.CSARUtils;
import eu.cloudopting.tosca.utils.CustomizationUtils;
import eu.cloudopting.tosca.utils.R10kResultHandler;
import eu.cloudopting.tosca.utils.ToscaUtils;
import jlibs.xml.sax.XMLDocument;
import jlibs.xml.xsd.XSInstance;
import jlibs.xml.xsd.XSParser;
import scala.annotation.meta.getter;

@Service
public class ToscaService {

	private final Logger log = LoggerFactory.getLogger(ToscaService.class);

	private HashMap<String, DefaultDirectedGraph<String, DefaultEdge>> graphHash = new HashMap<String, DefaultDirectedGraph<String, DefaultEdge>>();

	private XPathImpl xpath;

	private DocumentBuilderImpl db;

	private HashMap<String, DocumentImpl> xdocHash = new HashMap<String, DocumentImpl>();

	@Autowired
	private ToscaUtils toscaUtils;

	@Autowired
	private CustomizationUtils customizationUtils;

	@Autowired
	private CSARUtils csarUtils;

	@Value("${orchestrator.logger_address}")
	private String logger_address;

	public ToscaService() {
		super();
		XPathFactoryImpl xpathFactory = (XPathFactoryImpl) XPathFactoryImpl.newInstance();
		this.xpath = (XPathImpl) xpathFactory.newXPath();
		this.xpath.setNamespaceContext(new eu.cloudopting.tosca.xml.coNamespaceContext());
		this.xpath.setXPathFunctionResolver(new XPathFunctionResolverImpl());
		DocumentBuilderFactoryImpl dbf = new DocumentBuilderFactoryImpl();
		dbf.setNamespaceAware(true);

		try {
			this.db = (DocumentBuilderImpl) dbf.newDocumentBuilder();
		} catch (ParserConfigurationException e2) {
			// TODO Auto-generated catch block
			e2.printStackTrace();
		}
	}

	/**
	 * This method set the XML and creates the structures (DOM and graph) to be
	 * used in the next calls to the service
	 * 
	 * @param customizationId
	 *            the customizationId used to make the tosca service operate on
	 *            the correct XML in a multi user environment
	 * @param xml
	 *            the XML of the TOSCA customization taken from the DB
	 */
	public void setToscaCustomization(String customizationId, String xml) {
		// parse the string
		InputSource source = new InputSource(new StringReader(xml));
		DocumentImpl document = null;
		try {
			document = (DocumentImpl) this.db.parse(source);
			this.xdocHash.put(customizationId, document);
		} catch (SAXException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		// TODO add the graph part
		// log.info(this.xdocHash.toString());
		// Get the NodeTemplates
		DTMNodeList nodes = null;
		try {
			nodes = (DTMNodeList) this.xpath.evaluate("//ns:NodeTemplate", document, XPathConstants.NODESET);
		} catch (XPathExpressionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		// Get the RelationshipTemplate
		DTMNodeList relations = null;
		try {
			relations = (DTMNodeList) this.xpath.evaluate("//ns:RelationshipTemplate[@type='hostedOn']", document,
					XPathConstants.NODESET);
		} catch (XPathExpressionException e) {
			// TODO Auto-generated catch block
			log.debug("PROBLEMA IN PATH");
			log.debug(e.getMessage());
			e.printStackTrace();
		}

		// Now we create the Graph structure so we know the correct traversal
		// ordering
		ArrayList<String> values = new ArrayList<String>();
		DefaultDirectedGraph<String, DefaultEdge> g = new DefaultDirectedGraph<String, DefaultEdge>(DefaultEdge.class);
		for (int i = 0; i < nodes.getLength(); ++i) {
			// values.add(nodes.item(i).getFirstChild().getNodeValue());
			// System.out.println(nodes.item(i).getFirstChild().getNodeValue());
			// System.out.println(nodes.item(i).getAttributes().getNamedItem("id").getNodeValue());
			g.addVertex(nodes.item(i).getAttributes().getNamedItem("id").getNodeValue());
		}

		for (int i = 0; i < relations.getLength(); ++i) {
			// values.add(nodes.item(i).getFirstChild().getNodeValue());
			// System.out.println(nodes.item(i).getFirstChild().getNodeValue());
			NodeList nl = relations.item(i).getChildNodes();
			// System.out.println(nl.item(0).getNodeValue());
			// System.out.println(nl.item(1).getNodeValue());

			// System.out.println("relation s:"+
			// nl.item(1).getAttributes().getNamedItem("ref").getNodeValue());
			// System.out.println("relation t:"+
			// nl.item(3).getAttributes().getNamedItem("ref").getNodeValue());
			// System.out.println(relations.item(i).getFirstChild()
			// .getAttributes().getNamedItem("ref").getNodeValue());
			// System.out.println(relations.item(i).getAttributes()
			// .getNamedItem("id").getNodeValue());
			// this.g.addVertex(nodes.item(i).getAttributes().getNamedItem("id").getNodeValue());
			String sourceElement = null;
			String targetElement = null;
			for (int r = 0; r < nl.getLength(); r++) {
				Node el = nl.item(r);
				switch (el.getNodeName()) {
				case "SourceElement":
					sourceElement = el.getAttributes().getNamedItem("ref").getNodeValue();
					break;
				case "TargetElement":
					targetElement = el.getAttributes().getNamedItem("ref").getNodeValue();
					break;
				default:
					break;
				}

			}
			log.debug(targetElement);
			log.debug(sourceElement);
			g.addEdge(sourceElement, targetElement);
		}
		this.graphHash.put(customizationId, g);
		String v;
		TopologicalOrderIterator<String, DefaultEdge> orderIterator;

		orderIterator = new TopologicalOrderIterator<String, DefaultEdge>(g);
		// System.out.println("\nOrdering:");
		while (orderIterator.hasNext()) {
			v = orderIterator.next();
			// System.out.println(v);
		}

	}

	public void removeToscaCustomization(String customizationId) {
		this.graphHash.remove(customizationId);
		this.xdocHash.remove(customizationId);
		return;
	}

	public boolean validateToscaCsar(String csarPath) throws ToscaException {
		boolean isValid = true;
		if (csarPath.isEmpty()) {
			throw new ToscaException("File not good");
		}
		// Perform validation here and change the value of isValid accordingly
		return isValid;
	}

	public byte[] getToscaGraph(String customizationId) {
		log.debug("in getToscaGraph");
		DocumentImpl theDoc = this.xdocHash.get(customizationId);
		if (theDoc == null)
			return null;
		return null;

	}

	public String getOperationForNode(String customizationId, String id, String interfaceType) {
		log.debug("in getOperationForNode");
		log.debug("customizationId:" + customizationId);
		log.debug("id:" + id);
		DocumentImpl theDoc = this.xdocHash.get(customizationId);
		if (theDoc == null)
			return null;
		// log.debug(theDoc.saveXML(null));
		DTMNodeList nodes = null;
		// System.out.println("//ns:NodeType[@name=string(//ns:NodeTemplate[@id='"
		// + id + "']/@type)]/ns:Interfaces/ns:Interface[@name='" +
		// interfaceType + "']/ns:Operation/@name");
		/*
		 * String xq = "//ns:NodeType[@name=string(//ns:NodeTemplate[@id='" + id
		 * + "']/@type)]/ns:Interfaces/ns:Interface[@name='" + interfaceType +
		 * "']/ns:Operation/@name";
		 */
		String xq = "//ns:NodeType[@name=string(//ns:NodeTemplate[@id='" + id
				+ "']/@type)]/ns:Interfaces/ns:Interface[@name='" + interfaceType + "']/ns:Operation/@name";
		log.debug("xq:" + xq);
		try {
			nodes = (DTMNodeList) this.xpath.evaluate(xq, theDoc, XPathConstants.NODESET);
		} catch (XPathExpressionException e) {
			// TODO Auto-generated catch block
			log.debug("PROBLEMA IN PATH");
			log.debug(e.getMessage());
			e.printStackTrace();
		}
		// since there is a single ID we are sure that the array is with a
		// single element
		log.debug("nodes:" + new Integer(nodes.getLength()).toString());
		String template = nodes.item(0).getNodeValue();
		return template;
	}

	public DTMNodeList getNodesByType(String customizationId, String type) {
		log.debug("in getNodesByType");
		DocumentImpl theDoc = this.xdocHash.get(customizationId);
		if (theDoc == null)
			return null;

		log.debug("type:" + type);
		DTMNodeList nodes = null;
		try {
			nodes = (DTMNodeList) this.xpath.evaluate("//ns:NodeTemplate[@type='" + type + "']", theDoc,
					XPathConstants.NODESET);
		} catch (XPathExpressionException e) {
			// TODO Auto-generated catch block
			log.debug("PROBLEMA IN PATH");
			log.debug(e.getMessage());
			e.printStackTrace();
		}
		return nodes;
	}

	public void generatePuppetfile(String customizationId, String serviceHome) {
		ArrayList<String> modules = getPuppetModules(customizationId);
		log.debug(modules.toString());
		ArrayList<HashMap<String, String>> modData = new ArrayList<HashMap<String, String>>();
		for (String mod : modules) {
			modData.add(getPuppetModulesProperties(customizationId, mod));
			log.debug(mod);
		}
		log.debug(modData.toString());

		HashMap<String, Object> templData = new HashMap<String, Object>();
		templData.put("modData", modData);
		toscaUtils.generatePuppetfile(templData, serviceHome);
	}

	/**
	 * This method retrieve the tosca csar from the storage component and unzip
	 * it in the proper folder
	 * 
	 * @param customizationId
	 * @param service
	 * @param serviceHome
	 * @param provider
	 */
	public void manageToscaCsar(String customizationId, String service, String serviceHome, String provider,
			String toscaCsarPath) {
		log.debug("in manageToscaCsar");
		String fileName = service + ".czar";
		String path = "/cloudOptingData/";

		csarUtils.unzipToscaCsar(toscaCsarPath, serviceHome + "/tosca");
		/*
		 * try { toscaUtils.unzip(path + service + ".czar", serviceHome +
		 * "/tosca"); } catch (IOException e) { // TODO Auto-generated catch
		 * block e.printStackTrace(); }
		 */
	}

	public HashMap<String, String> getCloudData(String customizationId) {
		HashMap<String, String> retData = new HashMap<String, String>();
		retData.put("cpu", "1");
		retData.put("mamory", "1");
		retData.put("disk", "1");

		return retData;

	}

	public ArrayList<String> getArrNodesByType(String customizationId, String type) {
		DTMNodeList nodes = getNodesByType(customizationId, type);
		ArrayList<String> retList = new ArrayList<String>();
		System.out.println("before cycle");
		for (int i = 0; i < nodes.getLength(); ++i) {
			retList.add(nodes.item(i).getAttributes().getNamedItem("id").getNodeValue());
		}
		return retList;
	}

	public void getRootNode(String customizationId) {
		// getNodesByType("VMhost");
		return;
	}

	public String getTemplateForNode(String customizationId, String id, String templateType) {
		log.debug("in getTemplateForNode");
		DocumentImpl theDoc = this.xdocHash.get(customizationId);
		if (theDoc == null)
			return null;

		DTMNodeList nodes = null;
		log.debug("//ArtifactTemplate[@id=string(//NodeTemplate[@id='" + id
				+ "']/DeploymentArtifacts/DeploymentArtifact[@artifactType='" + templateType
				+ "']/@artifactRef)]/ArtifactReferences/ArtifactReference/@reference");
		try {

			nodes = (DTMNodeList) this.xpath.evaluate(
					"//ns:ArtifactTemplate[@id=string(//ns:NodeTemplate[@id='" + id
							+ "']/ns:DeploymentArtifacts/ns:DeploymentArtifact[@artifactType='" + templateType
							+ "']/@artifactRef)]/ns:ArtifactReferences/ns:ArtifactReference/@reference",
					theDoc, XPathConstants.NODESET);
		} catch (XPathExpressionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		// since there is a single ID we are sure that the array is with a
		// single element
		String template = nodes.item(0).getNodeValue();
		return template;
	}

	public ArrayList<String> getPuppetModules(String customizationId) {
		log.info("in getPuppetModules");
		DocumentImpl theDoc = this.xdocHash.get(customizationId);
		if (theDoc == null)
			return null;
		DTMNodeList modules = null;

		try {
			modules = (DTMNodeList) this.xpath.evaluate(
					"//ns:NodeTypeImplementation/ns:DeploymentArtifacts/ns:DeploymentArtifact[@artifactType='PuppetModule']/@artifactRef",
					theDoc, XPathConstants.NODESET);
		} catch (XPathExpressionException e) {
			// TODO Auto-generated catch block
			log.debug("PROBLEMA IN PATH");
			log.debug(e.getMessage());
			e.printStackTrace();
		}
		ArrayList<String> modulesList = new ArrayList<String>();

		for (int i = 0; i < modules.getLength(); ++i) {
			String module = modules.item(i).getNodeValue();
			modulesList.add(module);
		}

		return modulesList;
	}

	public HashMap<String, String> getPuppetModulesProperties(String customizationId, String module) {
		log.info("in getPuppetModulesProperties");
		log.info("module" + module);
		DocumentImpl theDoc = this.xdocHash.get(customizationId);
		if (theDoc == null)
			return null;
		DTMNodeList nodes = null;
		// System.out.println("//ArtifactTemplate[@id='" + module +
		// "']/Properties/*");
		try {
			nodes = (DTMNodeList) this.xpath.evaluate("//ns:ArtifactTemplate[@id='" + module + "']/ns:Properties/*",
					theDoc, XPathConstants.NODESET);
		} catch (XPathExpressionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		HashMap<String, String> propHash = new HashMap<String, String>();
		NodeList props = nodes.item(0).getChildNodes();
		for (int i = 0; i < props.getLength(); ++i) {
			// values.add(nodes.item(i).getFirstChild().getNodeValue());
			// System.out.println(nodes.item(i).getFirstChild().getNodeValue());

			// System.out.println("property val:" +
			// props.item(i).getTextContent());
			String[] keys = props.item(i).getNodeName().split(":");
			if (keys.length > 1) {
				String key = keys[1];
				// System.out.println("property:" + key);
				propHash.put(key, props.item(i).getTextContent());
			}
		}
		return propHash;
	}

	public ArrayList<String> getOrderedContainers(String customizationId) {
		log.debug("in getOrderedContainers");
		DocumentImpl theDoc = this.xdocHash.get(customizationId);
		if (theDoc == null)
			return null;
		ArrayList<String> dockerNodesList = new ArrayList<String>();
		dockerNodesList.add("ClearoApacheDC");
		dockerNodesList.add("ClearoMySQLDC");
		return dockerNodesList;
	}

	public HashMap getPropertiesForNode(String customizationId, String id) {
		log.debug("in getPropertiesForNode with id: " + id);
		DocumentImpl theDoc = this.xdocHash.get(customizationId);
		if (theDoc == null)
			return null;

		DTMNodeList nodes = null;
		// System.out.println("//NodeTemplate[@id='" + id + "']/Properties/*");
		try {
			nodes = (DTMNodeList) this.xpath.evaluate("//ns:NodeTemplate[@id='" + id + "']/ns:Properties/*", theDoc,
					XPathConstants.NODESET);
		} catch (XPathExpressionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		HashMap myHash = new HashMap();
		NodeList props = nodes.item(0).getChildNodes();
		log.debug(props.toString());

		for (int i = 0; i < props.getLength(); ++i) {
			String[] keys = props.item(i).getNodeName().split(":");
			String key = "";
			log.debug("value of keys -----------");
			log.debug(keys.toString());
			if (keys.length > 1) {
				key = keys[1];
			}
			log.debug("props.item(i):");
			log.debug(props.item(i).toString());
			log.debug("props.item(i).getFirstChild():");
			// log.debug(props.item(i).getFirstChild().toString());
			if (props.item(i).getFirstChild() != null) {
				if (props.item(i).getFirstChild().getNodeType() == Node.TEXT_NODE) {
					log.debug("HAS CHILD TEXT NODES ----------------------*****");
					myHash.put(key, props.item(i).getTextContent());
				} else {
					log.debug("HAS CHILD ELEMENT NODES *********++++++++++*****");
					ArrayList myArrChild = null;
					if (myHash.containsKey(key)) {
						myArrChild = (ArrayList) myHash.get(key);
					} else {
						myArrChild = new ArrayList();
					}
					HashMap myHashChild = new HashMap<>();
					for (int c = 0; c < props.item(i).getChildNodes().getLength(); c++) {
						String[] keysChild = props.item(i).getChildNodes().item(c).getNodeName().split(":");
						String keyChild = null;
						if (keysChild.length > 1) {
							keyChild = keysChild[1];
							log.debug("keyChild:" + keyChild);
						}
						myHashChild.put(keyChild, props.item(i).getChildNodes().item(c).getTextContent());
					}
					myArrChild.add(myHashChild);
					myHash.put(key, myArrChild);
				}
			}

		}
		log.debug(myHash.toString());
		return myHash;
	}

	public HashMap getPropertiesForNodeApplication(String customizationId, String id) {
		log.debug("in getPropertiesForNodeApplication");
		DocumentImpl theDoc = this.xdocHash.get(customizationId);
		if (theDoc == null)
			return null;

		DTMNodeList nodes = null;
		try {
			nodes = (DTMNodeList) this.xpath.evaluate("//ns:NodeTemplate[@id='" + id + "']/ns:Properties/*", theDoc,
					XPathConstants.NODESET);
		} catch (XPathExpressionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		HashMap<String, String> myHash = new HashMap<String, String>();
		NodeList props = nodes.item(0).getChildNodes();
		for (int i = 0; i < props.getLength(); ++i) {
			String key = props.item(i).getAttributes().getNamedItem("name").getNodeValue();
			myHash.put(key, props.item(i).getTextContent());
		}
		return myHash;
	}

	public ArrayList<String> getChildrenOfNode(String customizationId, String node) {
		log.debug("in getChildrenOfNode");
		DefaultDirectedGraph<String, DefaultEdge> graph = this.graphHash.get(customizationId);
		if (graph == null)
			return null;

		Set edges = graph.outgoingEdgesOf(node);
		log.debug("Children of:" + node + " are:" + edges.toString());
		Iterator<DefaultEdge> iterator = edges.iterator();
		ArrayList<String> children = new ArrayList<String>();
		while (iterator.hasNext()) {
			String target = graph.getEdgeTarget(iterator.next());
			children.add(target);
		}
		return children;
	}

	public ArrayList<String> getAllChildrenOfNode(String customizationId, String node) {
		log.debug("in getAllChildrenOfNode");
		ArrayList<String> children = new ArrayList<String>();
		children = getChildrenOfNode(customizationId, node);
		Iterator<String> child = children.iterator();
		ArrayList<String> returnChildren = new ArrayList<String>();
		while (child.hasNext()) {
			String theChild = child.next();
			returnChildren.addAll(getAllChildrenOfNode(customizationId, theChild));
			returnChildren.add(theChild);
		}

		return returnChildren;
	}

	public String getNodeType(String customizationId, String id) {
		log.debug("in getNodeType");
		DocumentImpl theDoc = this.xdocHash.get(customizationId);
		if (theDoc == null)
			return null;

		DTMNodeList nodes = null;

		try {
			nodes = (DTMNodeList) this.xpath.evaluate("//ns:NodeTemplate[@id='" + id + "']", theDoc,
					XPathConstants.NODESET);
		} catch (XPathExpressionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		// since there is a single ID we are sure that the array is with a
		// single element
		// We need to get the type
		String type = nodes.item(0).getAttributes().getNamedItem("type").getNodeValue();
		return type;
	}

	public String getServiceName(String customizationId) {
		log.info("in getServiceName");
		DocumentImpl theDoc = this.xdocHash.get(customizationId);
		if (theDoc == null)
			return null;
		DTMNodeList nodes = null;

		try {
			nodes = (DTMNodeList) this.xpath.evaluate("//ns:ServiceTemplate/@id", theDoc, XPathConstants.NODESET);
		} catch (XPathExpressionException e) {
			// TODO Auto-generated catch block
			log.debug("PROBLEMA IN PATH");
			log.debug(e.getMessage());
			e.printStackTrace();
		}
		// since there is a single ID we are sure that the array is with a
		// single element
		String serviceName = nodes.item(0).getNodeValue();
		log.debug("serviceName:" + serviceName);
		return serviceName;
	}

	public ArrayList<String> getExposedPortsOfChildren(String customizationId, String id) {
		log.debug("in getExposedPortsOfChildren");
		DocumentImpl theDoc = this.xdocHash.get(customizationId);
		if (theDoc == null)
			return null;

		ArrayList<String> exPorts = new ArrayList<String>();
		ArrayList<String> allChildren = getAllChildrenOfNode(customizationId, id);
		Iterator<String> aChild = allChildren.iterator();
		log.debug("all children" + allChildren.toString());
		ArrayList<String> xPathExprList = new ArrayList<String>();
		while (aChild.hasNext()) {
			xPathExprList.add(
					"//ns:NodeTemplate[@id='" + aChild.next() + "']/ns:Capabilities/ns:Capability/ns:Properties/*");
		}
		String xPathExpr = StringUtils.join(xPathExprList, "|");
		log.debug("xpath :" + xPathExpr);
		// since child nodes could not have ports to expose xpath could be ampty
		if (!xPathExpr.isEmpty()) {
			DTMNodeList nodes = null;
			try {
				nodes = (DTMNodeList) this.xpath.evaluate(xPathExpr, theDoc, XPathConstants.NODESET);
			} catch (XPathExpressionException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			for (int i = 0; i < nodes.getLength(); ++i) {
				exPorts.add(nodes.item(i).getTextContent());
			}
		}
		return exPorts;
	}

	public ArrayList<String> getContainerLinks(String customizationId, String id) {
		log.debug("in getContainerLinks");
		DocumentImpl theDoc = this.xdocHash.get(customizationId);
		if (theDoc == null)
			return null;

		DTMNodeList links = null;
		try {
			links = (DTMNodeList) this.xpath
					.evaluate("//ns:RelationshipTemplate[@type='containerLink']/ns:SourceElement[@ref='" + id
							+ "']/../ns:TargetElement", theDoc, XPathConstants.NODESET);
		} catch (XPathExpressionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		ArrayList<String> linksList = new ArrayList<String>();

		for (int i = 0; i < links.getLength(); ++i) {
			String link = links.item(i).getAttributes().getNamedItem("ref").getNodeValue();
			linksList.add(link);
		}

		return linksList;
	}

	public ArrayList<String> getVolumesFrom(String customizationId, String id) {
		log.debug("in getVolumesFrom");
		DocumentImpl theDoc = this.xdocHash.get(customizationId);
		if (theDoc == null)
			return null;

		DTMNodeList volumesFroms = null;
		try {
			volumesFroms = (DTMNodeList) this.xpath
					.evaluate("//ns:RelationshipTemplate[@type='volumeFrom']/ns:SourceElement[@ref='" + id
							+ "']/../ns:TargetElement", theDoc, XPathConstants.NODESET);
		} catch (XPathExpressionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		ArrayList<String> volumesFromList = new ArrayList<String>();

		for (int i = 0; i < volumesFroms.getLength(); ++i) {
			String volumesFrom = volumesFroms.item(i).getAttributes().getNamedItem("ref").getNodeValue();
			volumesFromList.add(volumesFrom);
		}

		return volumesFromList;
	}

	public ArrayList<HashMap> getVolumes(String customizationId, String id) {
		log.debug("in getVolumes");
		DocumentImpl theDoc = this.xdocHash.get(customizationId);
		if (theDoc == null)
			return null;

		HashMap props = getPropertiesForNode(customizationId, id);
		ArrayList<HashMap> volumesList = null;
		if (props.containsKey("volumes")) {
			return (ArrayList<HashMap>) props.get("volumes");
		}

		// ArrayList<HashMap> volumesList = new ArrayList<HashMap>();

		return null;
	}

	public String getLogType(String customizationId, String id) {
		log.debug("in getLogType");
		DocumentImpl theDoc = this.xdocHash.get(customizationId);
		if (theDoc == null)
			return null;

		HashMap props = getPropertiesForNode(customizationId, id);
		String logType = null;
		if (props.containsKey("logtype")) {
			return (String) props.get("logtype");
		}

		// ArrayList<HashMap> volumesList = new ArrayList<HashMap>();

		return null;
	}

	public ArrayList<String> getContainerPorts(String customizationId, String id) {
		log.debug("in getContainerPorts");
		DocumentImpl theDoc = this.xdocHash.get(customizationId);
		if (theDoc == null)
			return null;

		ArrayList<String> ports = new ArrayList<String>();
		String xPathExpr = new String("//ns:NodeTemplate[@id='" + id
				+ "']/ns:Capabilities/ns:Capability[@type='DockerContainerPortsCaps']/ns:Properties/co:DockerContainerPorts");

		DTMNodeList nodes = null;
		try {
			nodes = (DTMNodeList) this.xpath.evaluate(xPathExpr, theDoc, XPathConstants.NODESET);
		} catch (XPathExpressionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		System.out.println("nodes :" + nodes.getLength());
		for (int i = 0; i < nodes.getLength(); ++i) {
			if (nodes.item(i).getChildNodes().getLength() > 0) {
				String portInfo = nodes.item(i).getLastChild().getTextContent() + ":"
						+ nodes.item(i).getFirstChild().getTextContent();
				ports.add(portInfo);
				System.out.println("portInfo :" + portInfo);
			}
		}
		return ports;
	}

	public ArrayList<String> getHostPorts(String customizationId) {
		log.debug("in getHostPorts");
		DocumentImpl theDoc = this.xdocHash.get(customizationId);
		if (theDoc == null)
			return null;
		ArrayList<String> ports = new ArrayList<String>();

		String xPathExpr = new String(
				"//ns:NodeTemplate[@type='DockerContainer']/ns:Capabilities/ns:Capability[@type='DockerContainerPortsCaps']/ns:Properties/co:DockerContainerPorts");
		// System.out.println("xpath :" + xPathExpr);

		DTMNodeList nodes = null;
		try {
			XPathExpression expr = this.xpath.compile(xPathExpr);

			nodes = (DTMNodeList) this.xpath.evaluate(xPathExpr, theDoc, XPathConstants.NODESET);
			nodes = (DTMNodeList) expr.evaluate(theDoc, XPathConstants.NODESET);
		} catch (XPathExpressionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		System.out.println("nodes :" + nodes.getLength());
		for (int i = 0; i < nodes.getLength(); ++i) {
			// interested only in the host port that is the last
			// since the container could have no attributes we need before to
			// chack if there are children
			if (nodes.item(i).getChildNodes().getLength() > 0) {
				String portInfo = nodes.item(i).getLastChild().getTextContent();
				log.debug("portInfo:" + portInfo);
				log.debug("nodename:" + nodes.item(i).getLastChild().getNodeName());
				log.debug("nodetype:" + nodes.item(i).getLastChild().getNodeType());
				log.debug("textcontent:" + nodes.item(i).getLastChild().getTextContent());
				ports.add(portInfo);
				// System.out.println("portInfo :" + portInfo);

			}
		}
		return ports;
	}

	/*
	 * public void getPuppetModules(String customizationId, String id){ // here
	 * I get the puppet module list and use r10k to download them log.debug(
	 * "in getHostPorts"); DocumentImpl theDoc =
	 * this.xdocHash.get(customizationId); if (theDoc == null) return null;
	 * 
	 * }
	 */
	public void runR10k(String customizationId, String serviceHome, String coRoot) {
		log.debug("in getDefinitionFile");
		final long r10kJobTimeout = 95000;
		final boolean r10kInBackground = true;
		String puppetFile = serviceHome + "/Puppetfile";
		String puppetDir = coRoot + "/puppet/modules";
		log.debug("puppetFile:" + puppetFile);
		log.debug("puppetDir:" + puppetDir);

		R10kResultHandler r10kResult = toscaUtils.runR10k(puppetFile, puppetDir, r10kJobTimeout, r10kInBackground,
				serviceHome);

		try {
			r10kResult.waitFor();

		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return;

	}

	public void generateDockerCompose(String customizationId, String organizationName, String serviceHome,
			ArrayList<String> dockerNodesList) {
		ArrayList<HashMap<String, Object>> modData = new ArrayList<HashMap<String, Object>>();
		for (String node : dockerNodesList) {
			HashMap<String, Object> containerData = new HashMap<String, Object>();
			String imageName = "cloudopting/" + organizationName + "_" + node.toLowerCase();
			containerData.put("container", node);
			containerData.put("image", imageName);
			log.debug("dockerNodesList element working on: " + node);
			String logType = getLogType(customizationId, node);
			containerData.put("logtype", logType);
			containerData.put("log_driver_address", logger_address);
			// modData.add(toscaFileManager.getPuppetModulesProperties(mod));
			// get the link information for the node

			ArrayList<String> links = getContainerLinks(customizationId, node);
			if (links != null && !links.isEmpty()) {
				containerData.put("links", "   - " + StringUtils.join(links, "\n   - "));
			}
			ArrayList<String> exPorts = getExposedPortsOfChildren(customizationId, node);
			if (exPorts != null && !exPorts.isEmpty()) {
				containerData.put("exPorts", "   - \"" + StringUtils.join(exPorts, "\"\n   - \"") + "\"");
			}
			ArrayList<String> ports = getContainerPorts(customizationId, node);
			if (ports != null && !ports.isEmpty()) {
				containerData.put("ports", "   - \"" + StringUtils.join(ports, "\"\n   - \"") + "\"");
			}
			ArrayList<String> volumesFrom = getVolumesFrom(customizationId, node);
			if (volumesFrom != null && !volumesFrom.isEmpty()) {
				containerData.put("volumesFrom", "   - " + StringUtils.join(volumesFrom, "\n   - "));
			}
			ArrayList<HashMap> volumes = getVolumes(customizationId, node);
			if (volumes != null && !volumes.isEmpty()) {
				containerData.put("volumes", volumes);
			}

			System.out.println(node);
			modData.add(containerData);
		}
		System.out.println(modData.toString());

		HashMap<String, Object> templData = new HashMap<String, Object>();
		templData.put("dockerContainers", modData);
		// write the "Puppetfile" file
		toscaUtils.generateDockerCompose(templData, serviceHome);
	}

	public JSONObject getCustomizationFormData(Long idApp, String csarPath) {

		return customizationUtils.getCustomizationFormData(idApp, csarPath);

	}

	public String generateCustomizedTosca(Long idApp, String csarPath, JSONObject data, String organizationkey, String serviceName) {
		return customizationUtils.generateCustomizedTosca(idApp, csarPath, data, organizationkey, serviceName);

	}
}
