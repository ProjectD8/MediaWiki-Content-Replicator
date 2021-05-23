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

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Map;
import java.util.Stack;

public class XMLStringBuilder
{
	protected StringBuilder buffer;
	
	protected OutputStream stream;
	protected Writer writer;
	
	protected ArrayList<String> tabs;
	protected int indentation;
	
	protected Stack<String> open;
	
	public XMLStringBuilder() throws UnsupportedEncodingException
	{
		this(null, null);
	}
	
	public XMLStringBuilder(OutputStream os, String encoding) throws UnsupportedEncodingException
	{
		buffer = new StringBuilder();
		
		stream = os;
		writer = stream != null ? new OutputStreamWriter(stream, encoding) : null;
		
		tabs = new ArrayList();
		tabs.add("");
		
		indentation = 0;
		
		open = new Stack();
	}

	public Writer getWriter() throws IOException
	{
		return writer;
	}
	
	public OutputStream getOutputStream() throws IOException
	{
		return stream;
	}
	
	public void close() throws IOException
	{
		flush();
		
		if(writer != null)
		{
			writer.close();
		}
		
		writer = null;
		stream = null;
	}
	
	public void increaseIndentation()
	{
		if(++indentation >= tabs.size())
		{
			tabs.add(tabs.get(indentation - 1) + "\t");
		}
	}
	
	public void decreaseIndentation()
	{
		if(indentation > 0)
		{
			indentation--;
		}
	}
	
	public void resetIndentation()
	{
		indentation = 0;
	}
	
	public void println(String text)
	{
		buffer.append(tabs.get(indentation)).append(text).append("\r\n");
	}
	
	public void openTag(String tag)
	{
		openTag(tag, null);
	}
	
	public void openTag(String tag, Map<String, String> attributes)
	{
		buffer.append(tabs.get(indentation));
		buffer.append('<').append(open.push(tag));

		if(attributes != null)
		{
			for(Map.Entry<String, String> entry : attributes.entrySet())
			{
				buffer.append(' ').append(entry.getKey()).append("=\"").append(entry.getValue()).append('\"');
			}
		}

		buffer.append(">\r\n");
		increaseIndentation();
	}
	
	public void closeTag()
	{
		decreaseIndentation();
		buffer.append(tabs.get(indentation)).append("</").append(open.pop()).append(">\r\n");
	}
	
	public void append(String tag, String text)
	{
		append(tag, text, null);
	}
	
	public void append(String tag, String text, Map<String, String> attributes)
	{
		buffer.append(tabs.get(indentation));
		buffer.append('<').append(tag);

		if(attributes != null)
		{
			for(Map.Entry<String, String> entry : attributes.entrySet())
			{
				buffer.append(' ').append(entry.getKey()).append("=\"").append(entry.getValue()).append('\"');
			}
		}

		buffer.append('>');
		buffer.append(escapeText(text));
		buffer.append("</").append(tag).append(">\r\n");
	}
	
	public String toString()
	{
		return buffer.toString();
	}
	
	public void reset()
	{
		buffer = new StringBuilder();
	}
	
	public void flush() throws IOException
	{
		if(writer == null)
		{
			return; // throw new IllegalStateException("call setWriter() before calling flush()");
		}
		
		writer.write(buffer.toString());
		writer.flush();
		
		buffer.setLength(0);
	}
	
	public static String escapeText(String text)
	{
		text = text.replace("&", "&amp;");
		
		text = text.replace("<", "&lt;");
		text = text.replace(">", "&gt;");
		
		text = text.replace("\'", "&apos;");
		text = text.replace("\"", "&quot;");
		
		return text;
	}
}