package japicmp.output.xml;

import japicmp.config.Options;
import japicmp.exception.JApiCmpException;
import japicmp.exception.JApiCmpException.Reason;
import japicmp.model.JApiClass;
import japicmp.model.jsf.JsfComponent;
import japicmp.output.OutputFilter;
import japicmp.output.extapi.jpa.JpaAnalyzer;
import japicmp.output.extapi.jpa.model.JpaTable;
import japicmp.output.xml.model.JApiCmpXmlRoot;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.SchemaOutputResolver;
import javax.xml.transform.*;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.*;
import java.util.List;
import java.util.logging.Logger;

public class XmlOutputGenerator {
	private static final String XSD_FILENAME = "japicmp.xsd";
	private static final String XML_SCHEMA = XSD_FILENAME;
	private static final Logger LOGGER = Logger.getLogger(XmlOutputGenerator.class.getName());

	public void generate(String oldArchivePath, String newArchivePath, List<JApiClass> jApiClasses, Options options, List<JsfComponent> jsfComponents) {
		JApiCmpXmlRoot jApiCmpXmlRoot = createRootElement(oldArchivePath, newArchivePath, jApiClasses, jsfComponents);
		filterClasses(jApiClasses, options);
		createXmlDocumentAndSchema(options, jApiCmpXmlRoot);
	}

	private void createXmlDocumentAndSchema(Options options, JApiCmpXmlRoot jApiCmpXmlRoot) {
		try {
			JAXBContext jaxbContext = JAXBContext.newInstance(JApiCmpXmlRoot.class);
			Marshaller marshaller = jaxbContext.createMarshaller();
			marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
			marshaller.setProperty(Marshaller.JAXB_ENCODING, "UTF-8");
			marshaller.setProperty(Marshaller.JAXB_NO_NAMESPACE_SCHEMA_LOCATION, XML_SCHEMA);
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			marshaller.marshal(jApiCmpXmlRoot, baos);
			if (options.getXmlOutputFile().isPresent()) {
				final File xmlFile = new File(options.getXmlOutputFile().get());
				FileOutputStream fos = new FileOutputStream(xmlFile);
				baos.writeTo(fos);
				SchemaOutputResolver outputResolver = new SchemaOutputResolver() {
					@Override
					public Result createOutput(String namespaceUri, String suggestedFileName) throws IOException {
						File schemaFile = xmlFile.getParentFile();
						if (schemaFile == null) {
							LOGGER.warning(String.format("File '%s' has no parent file. Using instead: '%s'.", xmlFile.getAbsolutePath(), XSD_FILENAME));
							schemaFile = new File(XSD_FILENAME);
						} else {
							schemaFile = new File(schemaFile + File.separator + XSD_FILENAME);
						}
						StreamResult result = new StreamResult(schemaFile);
						result.setSystemId(schemaFile.toURI().toURL().toString());
						return result;
					}
				};
				jaxbContext.generateSchema(outputResolver);
			}
			if (options.getHtmlOutputFile().isPresent()) {
				TransformerFactory transformerFactory = TransformerFactory.newInstance();
				InputStream inputStream = XmlOutputGenerator.class.getResourceAsStream("/html.xslt");
				if(inputStream == null) {
					throw new JApiCmpException(Reason.XsltError, "Failed to load XSLT.");
				}
				Transformer transformer = transformerFactory.newTransformer(new StreamSource(inputStream));
				ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
				transformer.transform(new StreamSource(bais), new StreamResult(new FileOutputStream(options.getHtmlOutputFile().get())));
			}
		} catch (JAXBException e) {
			throw new JApiCmpException(Reason.JaxbException, String.format("Marshalling of XML document failed: %s", e.getMessage()), e);
		} catch (IOException e) {
			throw new JApiCmpException(Reason.IoException, String.format("Marshalling of XML document failed: %s", e.getMessage()), e);
		} catch (TransformerConfigurationException e) {
			throw new JApiCmpException(Reason.XsltError, String.format("Configuration of XSLT transformer failed: %s", e.getMessage()), e);
		} catch (TransformerException e) {
			throw new JApiCmpException(Reason.XsltError, String.format("XSLT transformation failed: %s", e.getMessage()), e);
		}
	}

	private void filterClasses(List<JApiClass> jApiClasses, Options options) {
		OutputFilter outputFilter = new OutputFilter(options);
		outputFilter.filter(jApiClasses);
	}

	private JApiCmpXmlRoot createRootElement(String oldArchivePath, String newArchivePath, List<JApiClass> jApiClasses, List<JsfComponent> jsfComponents) {
		JApiCmpXmlRoot jApiCmpXmlRoot = new JApiCmpXmlRoot();
		jApiCmpXmlRoot.setOldJar(oldArchivePath);
		jApiCmpXmlRoot.setNewJar(newArchivePath);
		jApiCmpXmlRoot.setClasses(jApiClasses);
		jApiCmpXmlRoot.setJsfComponents(jsfComponents);
		return jApiCmpXmlRoot;
	}
}
