package main;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Calendar;
import java.util.TimeZone;

import org.w3c.dom.Document;

import io.github.project.openubl.xmlbuilderlib.clock.SystemClock;
import io.github.project.openubl.xmlbuilderlib.config.Config;
import io.github.project.openubl.xmlbuilderlib.config.DefaultConfig;
import io.github.project.openubl.xmlbuilderlib.facade.DocumentManager;
import io.github.project.openubl.xmlbuilderlib.facade.DocumentWrapper;
import io.github.project.openubl.xmlbuilderlib.models.catalogs.Catalog6;
import io.github.project.openubl.xmlbuilderlib.models.input.common.ClienteInputModel;
import io.github.project.openubl.xmlbuilderlib.models.input.common.ProveedorInputModel;
import io.github.project.openubl.xmlbuilderlib.models.input.standard.DocumentLineInputModel;
import io.github.project.openubl.xmlbuilderlib.models.input.standard.invoice.InvoiceInputModel;
import io.github.project.openubl.xmlbuilderlib.models.output.standard.invoice.InvoiceOutputModel;
import io.github.project.openubl.xmlbuilderlib.utils.CertificateDetails;
import io.github.project.openubl.xmlbuilderlib.utils.CertificateDetailsFactory;
import io.github.project.openubl.xmlbuilderlib.xml.XMLSigner;
import io.github.project.openubl.xmlbuilderlib.xml.XmlSignatureHelper;
import io.github.project.openubl.xmlsenderws.webservices.managers.BillServiceManager;
import io.github.project.openubl.xmlsenderws.webservices.managers.smart.SmartBillServiceConfig;
import io.github.project.openubl.xmlsenderws.webservices.managers.smart.SmartBillServiceManager;
import io.github.project.openubl.xmlsenderws.webservices.managers.smart.SmartBillServiceModel;
import io.github.project.openubl.xmlsenderws.webservices.providers.BillServiceModel;
import io.github.project.openubl.xmlsenderws.webservices.wrappers.ServiceConfig;
import io.github.project.openubl.xmlsenderws.webservices.xml.XmlContentModel;

public class Main {

    public static void main(String[] args) throws Exception {
    	
        // Create XML (Crear XML)
        String xml = createUnsignedXML();

        System.out.println("Your XML is:");
        System.out.println(xml);

        // Sign XML (Firmar XML)
        Document signedXML = signXML(xml);

        // Documento generado
        byte[] bytesFromDocument = XmlSignatureHelper.getBytesFromDocument(signedXML);
        String signedXMLString = new String(bytesFromDocument, StandardCharsets.ISO_8859_1);

        
        System.out.println("\n Your signed XML is:");
        System.out.println(signedXMLString);
        
        guardarXMLFimado(signedXMLString);
        
        //"Send bill" Metodo! : Aqui guardamos manualmente el archivo XML
        /*ServiceConfig config = new ServiceConfig.Builder()
                .url("https://e-beta.sunat.gob.pe/ol-ti-itcpfegem-beta/billService")
                .username("RUC + USUARIOSOL")
                .password("CODIGOSOL")
                .build();
                
	    File file = new File(System.getProperty("user.dir") + "RUC-01-F001-1.xml");
	    BillServiceModel result = BillServiceManager.sendBill(file, config);
	    
	    System.out.println("Description : " + result.getDescription());
	    System.out.println("Ticket : " + result.getTicket());
	    System.out.println("Cdr : " + result.getCdr());
	    System.out.println("Code : " + result.getCode());
	    System.out.println("Status : " + result.getStatus());*/
        
	    //"Smart send" Metodo2 : Dejamos que el envio se haga de manera automatica solo le enviaremos el byteDocument[]
        SmartBillServiceConfig.getInstance()
	        .withInvoiceAndNoteDeliveryURL("https://e-factura.sunat.gob.pe/ol-ti-itcpfegem/billService")
	        .withPerceptionAndRetentionDeliveryURL("https://e-beta.sunat.gob.pe/ol-ti-itemision-otroscpe-gem-beta/billService")
	        .withDespatchAdviceDeliveryURL("https://e-beta.sunat.gob.pe/ol-ti-itemision-guia-gem-beta/billService");


        String username = "RUC + USUARIOSOL";
        String password = "CODIGOSOL";
        
        // Send file
        SmartBillServiceModel result1 = SmartBillServiceManager.send(bytesFromDocument, username, password);

        // Read result values
        XmlContentModel xmlData = result1.getXmlContentModel();
        BillServiceModel serverResponse = result1.getBillServiceModel();

        System.out.println("SUNAT status: " + serverResponse.getStatus());
        System.out.println("SUNAT ode: " + serverResponse.getCode());
        System.out.println("SUNAT Description: " + serverResponse.getDescription());
        System.out.println("SUNAT Ticket: " + serverResponse.getTicket());
        System.out.println("SUNAT Cdr: " + serverResponse.getCdr());

    }
    
    //Crear Archivo XML
    private static void guardarXMLFimado(String signedXMLString) {
    	
    	 try {
             String ruta = System.getProperty("user.dir") + "RUC-01-F001-1.xml";
             String contenido = signedXMLString;
             File file = new File(ruta);
             // Si el archivo no existe es creado
             if (!file.exists()) {
                 file.createNewFile();
             }
             FileWriter fw = new FileWriter(file);
             BufferedWriter bw = new BufferedWriter(fw);
             bw.write(contenido);
             bw.close();
         } catch (Exception e) {
             e.printStackTrace();
         }
    	
    }

    public static String createUnsignedXML() {
    	//Crear config por defecto
        Config config = new DefaultConfig();
        //Crear SystemClock
        SystemClock clock = new SystemClock() {
            @Override
            public TimeZone getTimeZone() {
                return TimeZone.getTimeZone("America/Lima");
            }

            @Override
            public Calendar getCalendarInstance() {
                return Calendar.getInstance();
            }
        };

        // Invoice generation
        InvoiceInputModel input = invoiceFactory();
        DocumentWrapper<InvoiceOutputModel> result = DocumentManager.createXML(input, config, clock);
        return result.getXml();
    }

    public static Document signXML(String xml) throws Exception {

    	//Buscar Archivo *.pfx
    	InputStream ksInputStream = new FileInputStream(new File(System.getProperty("user.dir") + "llaveprivada.pfx"));
    	CertificateDetails certificate = CertificateDetailsFactory.create(ksInputStream, "CODIGO");

    	X509Certificate x509Certificate = certificate.getX509Certificate();
    	PrivateKey privateKey = certificate.getPrivateKey();
    	
    	//Firmar XML
    	Document signedXML = XMLSigner.signXML(xml, "TU-FIRMA", x509Certificate, privateKey);
    	return signedXML;
    }

    public static InvoiceInputModel invoiceFactory() {
        return InvoiceInputModel.Builder.anInvoiceInputModel()
                .withSerie("F001")
                .withNumero(1)
                .withProveedor(ProveedorInputModel.Builder.aProveedorInputModel()
                        .withRuc("RUC - EMISOR")
                        .withRazonSocial("RAZON SOCIAL")
                        .build()
                )
                .withCliente(ClienteInputModel.Builder.aClienteInputModel()
                        .withNombre("CLIENTE")
                        //CLIENTE RUC
                        //.withNumeroDocumentoIdentidad("NUMERO RUC")
                        //.withTipoDocumentoIdentidad(Catalog6.RUC.toString())
                        //CLIENTE DNI
                        .withNumeroDocumentoIdentidad("NUMERO DNI")
                        .withTipoDocumentoIdentidad(Catalog6.DNI.toString())
                        .build()
                )
                .withDetalle(Arrays.asList(
                        DocumentLineInputModel.Builder.aDocumentLineInputModel()
                                .withDescripcion("Item1")
                                .withCantidad(new BigDecimal(1))
                                .withPrecioUnitario(new BigDecimal(1))
                                .build(),
                        DocumentLineInputModel.Builder.aDocumentLineInputModel()
                                .withDescripcion("Item2")
                                .withCantidad(new BigDecimal(1))
                                .withPrecioUnitario(new BigDecimal(1))
                                .build())
                )
                .build();
    }

}
