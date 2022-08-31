package br.com.caelum.camel.desafio.aula3;

import com.mysql.jdbc.jdbc2.optional.MysqlConnectionPoolDataSource;
import com.thoughtworks.xstream.XStream;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.http4.HttpMethods;
import org.apache.camel.dataformat.xstream.XStreamDataFormat;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.SimpleRegistry;

import java.text.SimpleDateFormat;

public class RotaPolling {

	public static void main(String[] args) throws Exception {
		final XStream xstream = new XStream();
		xstream.alias("negociacao", Negociacao.class);

		SimpleRegistry registro = new SimpleRegistry();
		registro.put("mysql", criaDataSource()); // The 'mysql' variable set, needs to be used when calling the database.

		CamelContext context = new DefaultCamelContext(registro); //construtor recebe registro
		context.addRoutes(new RouteBuilder() {
			@Override
			public void configure() throws Exception {

				from("timer://negociacoes?fixedRate=true&delay=1s&period=60s")
					//.setHeader(Exchange.HTTP_METHOD, HttpMethods.GET) // Send a GET to the URL below
					.to("http4://argentumws-spring.herokuapp.com/negociacoes") // Using the Apache Components version 4 (http4)
					.convertBodyTo(String.class) // Converts the IN message body to String. Transforma a entrada (InputStream), em uma String.
					//.log("MyBody = ${body}")
					.unmarshal(new XStreamDataFormat(xstream)) // All negociacao objects are added to a List<Negociacao>.
					.split(body()) // Every negociacao becomes a message.

					.process(new Processor() {
						@Override
						public void process(Exchange exchange) throws Exception {
							Negociacao negociacao = exchange.getIn().getBody(Negociacao.class);
							exchange.setProperty("preco", negociacao.getPreco());
							exchange.setProperty("quantidade", negociacao.getQuantidade());
							String data = new SimpleDateFormat("YYYY-MM-dd HH:mm:ss").format(negociacao.getData().getTime());
							exchange.setProperty("data", data);
						}
					})

					.setBody(simple("insert into negociacao(preco, quantidade, data) values (${property.preco}, ${property.quantidade}, '${property.data}')"))
					.log("Result = ${body}") // Logging SQL command
					.delay(10000) // Waiting 10s to facilitate understanding.
					.to("jdbc:mysql"); // Using the jdbc component that send the SQL to database. The database called 'mysql' was set previously.

//					.setHeader(Exchange.FILE_NAME, simple("henrique.xml")) //Adds a processor which sets the header on the IN message. Also creates a henrique.xml file with the IN.
//					.to("file:result"); // to() means 'enviar'. Using the 'file' component, send the results to the 'saida' folder.

			}
		});

		context.start();
		Thread.sleep(10000);
		context.stop();
	}

	private static MysqlConnectionPoolDataSource criaDataSource() {
		MysqlConnectionPoolDataSource mysqlDs = new MysqlConnectionPoolDataSource();
		mysqlDs.setDatabaseName("camel");
		mysqlDs.setServerName("localhost");
		mysqlDs.setPort(3306);
		mysqlDs.setUser("root");
		mysqlDs.setPassword("root");
		return mysqlDs;
	}

}
