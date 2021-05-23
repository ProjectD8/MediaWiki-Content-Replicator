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
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

public class RenameListParser extends DefaultHandler
{
	public static void getRenameList(Project project, ProgressMonitor progress) throws ParserConfigurationException, SAXException, IOException
	{
		progress.initProgress(true, false, false);
		progress.setOperationLimit(2);
		
		SAXParserFactory factory = SAXParserFactory.newInstance();
		SAXParser parser = factory.newSAXParser();
		
		RenameListParser handler = new RenameListParser(project);

		do
		{
			String request = project.getBaseURL() + "api.php?format=xml&action=query&list=logevents&letype=move&lelimit=max";

			if(handler.queryContinuePair != null)
			{
				progress.println("... " + handler.queryContinueFrom);

				request += "&" + handler.queryContinuePair;
				handler.queryContinuePair = null;
			}

			for(int tries = 0; tries < 5 && !progress.isCancelled(); tries++)
			{
				try
				{
					InputStream is = Util.openConnection(request);
					progress.setOperationProgress(1);
					
					parser.parse(is, handler);
					progress.setOperationProgress(2);
					
					is.close();
					progress.setOperationProgress(0);

					if(handler.parsed)
					{
						handler.parsed = false;
						break;
					}
					else
					{
						throw new IOException("empty result");
					}
				}
				catch(Throwable ex)
				{
					progress.showErrMsg(ex);

					try
					{
						Thread.sleep(5000);
					}
					catch(InterruptedException ie)
					{
					}
				}
			}
		}
		while(handler.queryContinuePair != null && !progress.isCancelled());
	}
	
	protected static final int MODE_NULL = 0;
	protected static final int MODE_ITEM = 1;
	protected static final int MODE_QUERY_CONTINUE = 2;
	
	protected Project project;
	protected WikiRename rename;
	protected int mode = MODE_NULL;
	
	public String queryContinueFrom = null;
	public String queryContinuePair = null;
	
	public boolean parsed = false;
	
	public RenameListParser(Project project)
	{
		this.project = project;
	}
	
	public void startElement(String uri, String localName, String qName, Attributes attributes)
	{
		if(qName.equals("item"))
		{
			int id = Integer.parseInt(attributes.getValue("logid"));
			String timestamp = attributes.getValue("timestamp");
			
			rename = project.getRename(id);
			
			if(rename == null)
			{
				rename = new WikiRename(id, timestamp);
				project.addRename(rename);
			}
			
			rename.setSource(Integer.parseInt(attributes.getValue("ns")), attributes.getValue("title"));
			
			rename.setUser(attributes.getValue("user"));
			rename.setComment(attributes.getValue("comment"));
			
			mode = MODE_ITEM;
		}
		else if(mode == MODE_ITEM && qName.equals("move"))
		{
			rename.setDestination(Integer.parseInt(attributes.getValue("new_ns")), attributes.getValue("new_title"));
			parsed = true;
		}
		else if(qName.equals("query-continue"))
		{
			mode = MODE_QUERY_CONTINUE;
		}
		else if(mode == MODE_QUERY_CONTINUE && qName.equals("logevents"))
		{
			try
			{
				queryContinueFrom = attributes.getValue(0);
				queryContinuePair = attributes.getQName(0) + "=" + URLEncoder.encode(queryContinueFrom, "UTF-8");
			}
			catch(UnsupportedEncodingException ex)
			{
				queryContinuePair = null;
			}
		}
	}
	
	public void endElement(String uri, String localName, String qName)
	{
		if(qName.equals("item") || qName.equals("query-continue"))
		{
			rename = null;
			mode = MODE_NULL;
		}
	}
}