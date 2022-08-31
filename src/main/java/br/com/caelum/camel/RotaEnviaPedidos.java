package br.com.caelum.camel;

import org.apache.activemq.camel.component.ActiveMQComponent;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.http4.HttpMethods;
import org.apache.camel.impl.DefaultCamelContext;

/**
 * Route that sends Orders to the ActiveMQ
 */
public class RotaEnviaPedidos {

	public static void main(String[] args) throws Exception {
		CamelContext context = new DefaultCamelContext();
		context.addComponent("activemq", ActiveMQComponent.activeMQComponent("tcp://localhost:61616"));

		context.addRoutes(new RouteBuilder() {
			@Override
			public void configure() throws Exception {

				from("file:pedidos?noop=true") // Using the component 'file' go to the 'pedidos' folder. 'noop=true' means that to copy/paste files (without it would cut/paste)
					.to("activemq:queue:pedidos"); // Send all xmls on the pedidos folder to the queue called 'pedidos'.

			}
		});


		context.start();
		Thread.sleep(10000);
		context.stop();
	}	
}
