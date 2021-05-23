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

public class AllPagesListParser extends DefaultHandler
{
	public static void getAllPagesList(Project project, ProgressMonitor progress) throws ParserConfigurationException, SAXException, IOException
	{
		progress.initProgress(true, false, true);
		progress.setOperationLimit(2);
		progress.setProjectLimit(project.listNamespaces().size());
		
		SAXParserFactory factory = SAXParserFactory.newInstance();
		SAXParser parser = factory.newSAXParser();
		
		AllPagesListParser handler = new AllPagesListParser(project);
		
		for(WikiNamespace ns : project.listNamespaces())
		{
			progress.println("Processing namespace " + ns.getID() + " (" + ns.getName() + ")...");

			do
			{
				String request = project.getBaseURL() + "api.php?format=xml&action=query&list=allpages&aplimit=max&apnamespace=" + ns.getID();

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
			
			progress.progressProject(1);
			
			if(progress.isCancelled())
			{
				break;
			}
		}
	}
	
	protected static final int MODE_NULL = 0;
	protected static final int MODE_QUERY = 1;
	protected static final int MODE_QUERY_CONTINUE = 2;
	
	protected Project project;
	protected int mode = MODE_NULL;
	
	public String queryContinueFrom = null;
	public String queryContinuePair = null;
	
	public boolean parsed = false;
	
	public AllPagesListParser(Project project)
	{
		this.project = project;
	}
	
	public void startElement(String uri, String localName, String qName, Attributes attributes)
	{
		if(qName.equals("query"))
		{
			mode = MODE_QUERY;
		}
		else if(qName.equals("query-continue"))
		{
			mode = MODE_QUERY_CONTINUE;
		}
		else if(mode == MODE_QUERY && qName.equals("p"))
		{
			int id = Integer.parseInt(attributes.getValue("pageid"));
			int ns = Integer.parseInt(attributes.getValue("ns"));
			
			WikiPage page = project.getPage(id);
			
			if(page == null)
			{
				page = new WikiPage(id, ns);
				project.addPage(page);
			}
			
			page.setTitle(attributes.getValue("title"));
			page.setNS(ns);
			
			project.invalidateIndex();
			
			parsed = true;
		}
		else if(mode == MODE_QUERY_CONTINUE && qName.equals("allpages"))
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
		if(qName.equals("query") || qName.equals("query-continue"))
		{
			mode = MODE_NULL;
		}
	}
}