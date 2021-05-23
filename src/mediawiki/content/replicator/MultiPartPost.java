/*
 * Copyright 2015 Kulikov Dmitriy
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package mediawiki.content.replicator;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class MultiPartPost
{
	public static final char[] MULTIPART_CHARS = "1234567890abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();
	public static final Random rnd = new Random();
	
	public static String generateBoundary()
	{
		StringBuilder buf = new StringBuilder(40);
		
		for(int i = 0; i < 40; i++)
		{
			buf.append(MULTIPART_CHARS[rnd.nextInt(MULTIPART_CHARS.length)]);
		}
		
		return buf.toString();
	}
	
	protected static class FileElement implements FormDataElement
	{
		private final InputStream in;

		public FileElement(InputStream in)
		{
			this.in = in;
		}

		public void writeElement(Writer writer, OutputStream out) throws IOException
		{
			writer.flush();
			
			byte[] buf = new byte[0x10000];
		
			while(in.available() > 0)
			{
				out.write(buf, 0, in.read(buf));
			}
			
			out.flush();
		}
	}
	
	protected static class ElementInfo
	{
		private final String name;
		private final String filename;
		private final String value;
		
		private final boolean isBinary;
		
		private final FormDataElement contents;

		public ElementInfo(String name, String value)
		{
			this.name = name;
			this.value = value;
			
			filename = null;
			contents = null;
			isBinary = false;
		}

		public ElementInfo(String name, String filename, boolean isBinary, FormDataElement contents)
		{
			this.name = name;
			this.filename = filename;
			this.isBinary = isBinary;
			this.contents = contents;
			
			value = null;
		}

		public String getName()
		{
			return name;
		}

		public String getFileName()
		{
			return filename;
		}
		
		public boolean hasFileName()
		{
			return filename != null;
		}

		public String getValue()
		{
			return value;
		}
		
		public boolean hasValue()
		{
			return value != null;
		}

		public boolean isBinary()
		{
			return isBinary;
		}

		public FormDataElement getContents()
		{
			return contents;
		}
	}
	
	protected HashMap<String, String> cookies;
	protected String response;
	
	protected ArrayList<ElementInfo> elements;
	
	protected String boundary;
	
	public MultiPartPost() throws IOException
	{
		cookies = new HashMap();
		elements = new ArrayList();
		
		reset();
	}
	
	public void reset() throws IOException
	{
		elements.clear();
		boundary = generateBoundary();
	}
	
	public void addParam(String name, String value) throws IOException
	{
		elements.add(new ElementInfo(name, value));
	}
	
	public void addFile(String name, String filename, InputStream in)
	{
		elements.add(new ElementInfo(name, filename, true, new FileElement(in)));
	}
	
	public void addElement(String name, String filename, boolean isBinary, FormDataElement contents)
	{
		elements.add(new ElementInfo(name, filename, isBinary, contents));
	}
	
	public String post(String url) throws IOException
	{
		HttpURLConnection connection = (HttpURLConnection)(new URL(url)).openConnection();
		
		connection.setRequestMethod("POST");
		connection.setInstanceFollowRedirects(true);
		
		connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
		setCookies(connection);
		
		connection.setDoOutput(true);
		connection.setChunkedStreamingMode(1024 * 1024);
		connection.connect();
		
		OutputStream out = connection.getOutputStream();
		Writer writer = new BufferedWriter(new OutputStreamWriter(out, "UTF-8"));
		
		writer.append("\r\n");
		writer.append("--").append(boundary);
		
		for(ElementInfo element : elements)
		{
			writer.append("\r\n");
			
			if(element.hasFileName())
			{
				writer.append("Content-Disposition: form-data; name=\"").append(element.getName()).append("\"; filename=\"").append(element.getFileName()).append("\"\r\n");
			}
			else
			{
				writer.append("Content-Disposition: form-data; name=\"").append(element.getName()).append("\"\r\n");
			}
			
			if(element.isBinary())
			{
				writer.append("Content-Type: application/octet-stream; charset=UTF-8\r\n");
				writer.append("Content-Transfer-Encoding: binary\r\n");
			}
			else
			{
				writer.append("Content-Type: text/plain; charset=UTF-8\r\n");
				writer.append("Content-Transfer-Encoding: 8bit\r\n");
			}
			
			writer.append("\r\n");
			
			if(element.hasValue())
			{
				writer.append(element.getValue());
			}
			else
			{
				writer.flush();
				element.getContents().writeElement(writer, out);
			}
			
			writer.append("\r\n").append("--").append(boundary);
		}
		
		writer.append("--\r\n\r\n");
		writer.flush();
		
		out.close();
		
		reset();
		
		grabCookies(connection);
		
		BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream(), "UTF-8"));
		String line;
		
		StringBuilder buf = new StringBuilder();
		
		while((line = br.readLine()) != null)
		{
			buf.append(line).append('\n');
		}
		
		br.close();
		
		return response = buf.toString();
	}
	
	public void setCookies(URLConnection connection)
	{
		if(!cookies.isEmpty())
		{
			StringBuilder cookie = new StringBuilder();

			for(Map.Entry<String, String> entry : cookies.entrySet())
			{
				cookie.append(entry.getKey());
				cookie.append("=");
				cookie.append(entry.getValue());
				cookie.append("; ");
			}

			connection.setRequestProperty("Cookie", cookie.toString());
		}

		connection.setRequestProperty("User-Agent", Util.USER_AGENT);
	}
	
	public void grabCookies(URLConnection connection)
	{
		String headerName;
		
		for(int i = 1; (headerName = connection.getHeaderFieldKey(i)) != null; i++)
		{
			if(headerName.equals("Set-Cookie"))
			{
				String cookie = connection.getHeaderField(i);
				
				int index = cookie.indexOf(';');
				
				if(index > 0)
				{
					cookie = cookie.substring(0, index);
				}
				
				index = cookie.indexOf('=');
				
				String name = cookie.substring(0, index);
				String value = cookie.substring(index + 1);
				
				cookies.put(name, value);
			}
		}
	}
	
	public HashMap<String, String> getCookies()
	{
		return cookies;
	}
	
	public void clearCookies()
	{
		cookies.clear();
	}
	
	public String getResponse()
	{
		return response;
	}
	
	public void printResponse()
	{
		System.out.println("Cookies:");

		for(Map.Entry<String, String> entry : cookies.entrySet())
		{
			System.out.println(entry.getKey() + " = " + entry.getValue());
		}

		System.out.println("Response:");
		System.out.println(response);
	}
}