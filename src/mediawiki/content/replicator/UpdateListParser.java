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
import java.util.ArrayList;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.TransformerException;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

public class UpdateListParser extends DefaultHandler
{
	public static void markForUpdates(Project project, ProgressMonitor progress, int pagesPerRequest, long projectSaveInterval) throws ParserConfigurationException, SAXException, IOException, TransformerException
	{
		progress.initProgress(true, true, true);
		progress.setOperationLimit(2);
		
		SAXParserFactory factory = SAXParserFactory.newInstance();
		SAXParser parser = factory.newSAXParser();
		
		long currentTime = System.currentTimeMillis();
		long projectSaveTime = currentTime + projectSaveInterval;
		
		int totalPages = project.countPages();
		int parsedPages = 0;
		int updatedPages = 0;
		
		progress.setProjectLimit(totalPages);
		
		UpdateListParser handler = new UpdateListParser(project, progress);
		
		for(WikiNamespace ns : project.listNamespaces())
		{
			progress.println("Processing namespace " + ns.getID() + " (" + ns.getName() + ")...");

			ArrayList<WikiPage> pages = project.listPages(ns.getID());
			
			if(pages == null)
			{
				continue;
			}
			
			int index = 0, last = pages.size();

			while(index < last && !progress.isCancelled())
			{
				StringBuilder buf = new StringBuilder();
				int batch = 0;

				while(index < last && batch < pagesPerRequest)
				{
					if(batch > 0)
					{
						buf.append('|').append(pages.get(index).getID());
					}
					else
					{
						buf.append(pages.get(index).getID());
					}

					batch++;
					index++;
				}

				if(batch > 0)
				{
					String request = project.getBaseURL() +
									 "api.php?format=xml&action=query&prop=revisions" +
									 "&rvprop=ids" +
									 "&rvdir=older" +
									 "&pageids=" + buf.toString();

					// progress.println(request);

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

					if(handler.parsedPages < batch && !progress.isCancelled())
					{
						throw new IOException("parsed " + handler.parsedPages + " pages when requested " + batch);
					}

					parsedPages += handler.parsedPages;
					updatedPages += handler.updatedPages;

					handler.parsedPages = 0;
					handler.updatedPages = 0;
					
					progress.setProjectProgress(parsedPages);
					
					progress.setPageLimit(parsedPages);
					progress.setPageProgress(parsedPages - updatedPages);

					// progress.println("[" + parsedPages + "/" + totalPages + "] " + updatedPages);
					progress.println("... " + parsedPages + " / " + totalPages + " parsed, " + updatedPages + " need update");

					currentTime = System.currentTimeMillis();

					if(currentTime >= projectSaveTime)
					{
						System.gc();
						project.write(progress);
						projectSaveTime = currentTime + projectSaveInterval;
					}
				}
			}
			
			if(progress.isCancelled())
			{
				break;
			}
		}
	}
	
	protected static final int MODE_NULL = 0;
	protected static final int MODE_PAGE = 1;
	protected static final int MODE_REVISION = 2;
	
	protected Project project;
	protected ProgressMonitor progress;
	protected WikiPage page;
	protected int mode = MODE_NULL;
	
	public int parsedPages = 0;
	public int updatedPages = 0;
	
	public boolean parsed = false;
	
	public UpdateListParser(Project project, ProgressMonitor progress)
	{
		this.project = project;
		this.progress = progress;
	}
	
	public void startElement(String uri, String localName, String qName, Attributes attributes)
	{
		if(qName.equals("page"))
		{
			page = project.getPage(Integer.parseInt(attributes.getValue("pageid")));
			
			if(page != null)
			{
				parsedPages++;
				
				try
				{
					page.load(progress);
				}
				catch(Throwable ex)
				{
					progress.showErrMsg(ex);
				}
				
				if(page.requiresDownload() || !page.isActual())
				{
					updatedPages++;
					page.setActual(false);
				}
				else
				{
					mode = MODE_PAGE;
				}
			}
			
			parsed = true;
		}
		else if(mode == MODE_PAGE && qName.equals("rev"))
		{
			int newid = Integer.parseInt(attributes.getValue("revid"));
			int oldid = page.getNewestRevisionID();
			
			if(newid > oldid)
			{
				if(page.isActual())
				{
					updatedPages++;
				}
				
				page.setActual(false);
			}
		}
	}
	
	public void endElement(String uri, String localName, String qName)
	{
		if(qName.equals("page"))
		{
			if(page != null)
			{
				try
				{
					page.unload(true);
				}
				catch(Throwable ex)
				{
					progress.showErrMsg(ex);
				}
			}
			
			page = null;
			mode = MODE_NULL;
		}
		else if(mode == MODE_REVISION && qName.equals("rev"))
		{
			mode = MODE_PAGE;
		}
	}
}