package br.com.caelum.camel;

import org.apache.activemq.camel.component.ActiveMQComponent;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.http4.HttpMethods;
import org.apache.camel.impl.DefaultCamelContext;
import org.xml.sax.SAXParseException;

public class RotaPedidos {

	public static void main(String[] args) throws Exception {

		CamelContext context = new DefaultCamelContext();
		context.addComponent("activemq", ActiveMQComponent.activeMQComponent("tcp://localhost:61616"));

		context.addRoutes(new RouteBuilder() {
			@Override
			public void configure() throws Exception {

				// =============== Handling Exceptions =================
				/*
					Scenario: Lots of transformations were executed but the last one (or any other during the process) failed. The errorHandler will handle the exception from the moment that the route threw the Exception,
					which means, it will catch the Message that went through transformations. But if I want, I can catch the original message as well. For example:
					errorHandler(deadLetterChannel("activemq:queue:pedidos.DLQ").useOriginalMessage().maximumRedeliveries(3).redeliveryDelay(5000);
				 */
				// Example 1
				// Error handler needs to be created at the beginning (before calling any route).
				//errorHandler(deadLetterChannel("file:erro") // deadLetterChannel() is a route to my errors. This line creates a 'erro' folder and add the error in there.
				errorHandler(deadLetterChannel("activemq:queue:pedidos.DLQ") // deadLetterChannel() is a route to my errors. This line creates a 'erro' folder and add the error in there.
					.logExhaustedMessageHistory(true) // Show logs on the console.
					.maximumRedeliveries(3).redeliveryDelay(3000)  // Retry 3 times and wait 3s before each attempt.
					.onRedelivery(new Processor() {
						@Override
						public void process(Exchange exchange) throws Exception {
							int counter = (int) exchange.getIn().getHeader(Exchange.REDELIVERY_COUNTER);
							int max = (int) exchange.getIn().getHeader(Exchange.REDELIVERY_MAX_COUNTER);
							System.out.println("===> Redelivery 1 <=== || counter: " + counter + " || max: " + max);
						}
					})
				);

				// Example 2.
				// Error handler needs to be created at the beginning (before calling any route).
//				onException(SAXParseException.class) // I could use any Exception: Exception.class, SAXParseException.class, IOException, etc.
//					.handled(true) // Remove the message that has problem out of the route.
//					.maximumRedeliveries(3).redeliveryDelay(4000)  // Retry 3 times and wait 4s before each attempt.
//					.onRedelivery(new Processor() {
//						@Override
//						public void process(Exchange exchange) throws Exception {
//							int counter = (int) exchange.getIn().getHeader(Exchange.REDELIVERY_COUNTER);
//							int max = (int) exchange.getIn().getHeader(Exchange.REDELIVERY_MAX_COUNTER);
//							System.out.println("Redelivery - " + counter + "/" + max );;
//						}
//					});

				//Example 3: Pointing to a different endpoint.
				//onException(SAXParseException.class).handled(false).to("file:error-parsing");

				// =============== End of Handling Exceptions =================

				// from() => This is the place where all the data is coming from.
				//from("file:pedidos?delay=5s&noop=true") // Using the component 'file' go to the 'pedidos' folder and also execute every 5s. 'noop=true' means that to copy/paste files (without it would cut/paste). It also means that I do not process files when they are the same.
				from("activemq:queue:pedidos") // Replacing file component and now using the component 'activemq' that was added on line 16.
					.log("${file:name}") // Logging filenames
					.routeId("rota-pedidos") // Creating ID for this route.
					//.delay(1000) // Creates a 1s delay between each attempt.
					.to("validator:pedido.xsd") // Validating the XMLs files through the pedido.xsd schema.
					.multicast() // Multicast messages to all its child outputs. It means that the messages I have that come from 'from("file:pedidos?delay=5s&noop=true")' will be the same for all children.
								 // So, each processor and destination gets a copy of the original message to avoid the processors interfering with each other.
					//.multicast().parallelProcessing().timeout(500) // This line just shows that multicast() can invoke sub routes in different threads, and they are processed in parallel. NOTE: This line was not part of the code, just an example to show this capability.
					.to("direct:soap") // Call the sub route 'soap'. Without the multicast(), the return from the sub route 'soap' would be returned, instead of the XML files' values.
					.to("direct:http"); // Call the sub route 'http'

				from("direct:http") // Creating a sub route called http. This sub route can be called from anywhere. Direct is a synchronous processing.
					.routeId("rota-http") // Creating ID for this route.
					.setProperty("pedidoId", xpath("/pedido/id/text()")) // Creating a variable/property called pedidoId, which contains the value on <id>...</id> from the XML.
					//.setProperty("emailId", xpath("/pedido/pagamento/email-titular/text()")) // Creating a variable/property called emailId
					.setProperty("clientId", xpath("/pedido/pagamento/email-titular/text()")) // Creating a variable/property called pedidoId.
					.split().xpath("/pedido/itens/item") // Get each <item> of all the XML files.
					.filter().xpath("/item/formato[text()='EBOOK']") // Filter for XML formats. With all the <items> in hand, filter the ones that contain <formato>EBOOK</formato>
					.setProperty("ebookId", xpath("/item/livro/codigo/text()")) // Creating a variable/property called ebookId.
					.marshal() // Transform the message into a different format.
						.xmljson() // Transform the XML content to JSON
					.log("HTTP BODY <===> ${body}")

					//.setHeader("CamelFileName", simple("${file:name.noext}.json")) // Maintain the filenames but change their extension to .json
					//.to("file:saida"); // to() means 'enviar'. Using the 'file' component, send the results to the 'saida' folder.

					//.setHeader(Exchange.HTTP_METHOD, HttpMethods.POST) // Send a POST to the URL below
					//.to("http4://localhost:8080/webservices/ebook/item"); // Using the Apache Components version 4 (http4)

					//.setHeader(Exchange.HTTP_METHOD, HttpMethods.GET) // Send a GET to the URL below
					//.setHeader(Exchange.HTTP_QUERY, constant("ebookId=ARQ&pedidoId=2451256&clienteId=email.com.au")) // Send a GET with the parameters on constant(...).
						// constant() cannot read properties. It does not interpret an Expression Language (EL).

					.setHeader(Exchange.HTTP_METHOD, HttpMethods.GET) // Send a GET to the URL below
					.setHeader(Exchange.HTTP_QUERY, simple("ebookId=${property.ebookId}&pedidoId=${property.pedidoId}&clienteId=${property.clientId}")) // Send a GET with query params (dynamically).
						// simple() can be used to read properties. It can interpret an Expression Language.
					.to("http4://localhost:8080/webservices/ebook/item"); // Using the Apache Components version 4 (http4). The GET URL would be: http4://localhost:8080/webservices/ebook/item/ebookId=ARQ&pedidoId=2451256&clienteId=email.com.au

					// This from() creates SOAP envelops.
					from("direct:soap") // Creating a sub route called soap. This sub route can be called from anywhere.
						.routeId("rota-soap") // Creating ID for this route.
						//.setBody(constant("<envelop>test henrique</envelop>")) // This will override my current body value. But if I use the multicast (begin of the code), this processor will not change the body for the next route.
						.to("xslt:pedido-para-soap.xslt") // Map the body, which is an XML, to the XSL created. This way, I can create the SOAP envelop.
							// Other options to find a template are: to("xslt:br/com/caelum/xslt/pedidos-para-soap.xslt"), to("xslt:file://C:\xslt\pedidos-para-soap.xslt")
						.log("SOAP BODY <===> ${body}")
						//.to("mock:soap") // I can use mock() when I do not have an endpoint defined, and I want to simulate a route. For example, a customer has not released it yet.
						.setHeader(Exchange.CONTENT_TYPE, constant("text/xml"))
						.to("http4://localhost:8080/webservices/financeiro"); // Because I have a body set, it will automatically call the POST method.


			}
		});


		context.start();
		Thread.sleep(10000);
		context.stop();
	}	
}
