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
import java.io.InputStream;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

public class NamespaceParser extends DefaultHandler
{
	public static void getNamespaces(Project project) throws ParserConfigurationException, SAXException, IOException
	{
		SAXParserFactory factory = SAXParserFactory.newInstance();
		SAXParser parser = factory.newSAXParser();
		
		NamespaceParser handler = new NamespaceParser(project);
		
		String request = project.getBaseURL() + "api.php?format=xml&action=query&meta=siteinfo&siprop=namespaces";
		
		InputStream is = Util.openConnection(request);
		parser.parse(is, handler);
		is.close();
	}
	
	public static final int MODE_NULL = 0;
	public static final int MODE_NAMESPACE = 1;
	
	protected Project project;
	protected WikiNamespace current;
	protected StringBuilder buf;
	protected int mode = MODE_NULL;
	
	public NamespaceParser(Project project)
	{
		this.project = project;
		buf = new StringBuilder();
	}
	
	public void startElement(String uri, String localName, String qName, Attributes attributes)
	{
		if(qName.equals("ns"))
		{
			int id = Integer.parseInt(attributes.getValue("id"));
			
			if(id < 0)
			{
				return;
			}
			
			current = project.getNamespace(id);
			
			if(current == null)
			{
				current = new WikiNamespace(id);
				project.addNamespace(current);
			}
			
			mode = MODE_NAMESPACE;
		}
	}
	
	public void characters(char[] ch, int start, int length)
	{
		if(mode == MODE_NAMESPACE)
		{
			buf.append(ch, start, length);
		}
	}
	
	public void endElement(String uri, String localName, String qName)
	{
		if(mode == MODE_NAMESPACE && qName.equals("ns"))
		{
			current.setName(buf.toString());
			buf.setLength(0);
			
			mode = MODE_NULL;
			current = null;
		}
	}
}