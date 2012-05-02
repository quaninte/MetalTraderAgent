package agent.trader.user.cnpm1.nhom6;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URL;

import org.omg.CORBA.portable.InputStream;

public class Log {
	
	public static String newTestUrl = "http://localhost/metalslog/test/newTest";
	public static String updatePriceUrl = "http://localhost/metalslog/test/updateMetal/";
	public static String addTrend = "http://localhost/metalslog/test/addTrend/";
	
	/**
	 * Create a new test
	 * @throws Exception
	 */
	public static void newTest()
	{
		try {
			URL url = new URL(Log.newTestUrl);
			InputStreamReader in = new InputStreamReader(url.openStream());  
			StringWriter out = new StringWriter();  
			String content = out.toString();
		} catch(Exception e) {
			System.err.println("No connection for log - new test!");
			System.err.println("Got an IOException: " + e.getMessage());
		}
	}
	
	/**
	 * Update price when testing
	 * @param type
	 * @param price
	 */
	public static void updatePrice(String type, double price)
	{
		try {
			URL url = new URL(Log.updatePriceUrl + type + "/" + price);
			InputStreamReader in = new InputStreamReader(url.openStream());  
			StringWriter out = new StringWriter();  
			String content = out.toString();		
		} catch(Exception e) {
			System.err.println("No connection for log - update price!");
			System.err.println("Got an IOException: " + e.getMessage());
		}
	}

	public static void addTrend(String type, String direction) {

		try {
			URL url = new URL(Log.addTrend + type + "/" + direction);
			InputStreamReader in = new InputStreamReader(url.openStream());  
			StringWriter out = new StringWriter();  
			String content = out.toString();		
		} catch(Exception e) {
			System.err.println("No connection for log - add trend!");
			System.err.println("Got an IOException: " + e.getMessage());
		}
	}
}
