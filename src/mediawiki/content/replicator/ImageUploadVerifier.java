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

public class ImageUploadVerifier extends DefaultHandler
{
	public static void verifyImages(Project project, ProgressMonitor progress, int pagesPerRequest, long projectSaveInterval) throws ParserConfigurationException, SAXException, IOException, TransformerException
	{
		progress.initProgress(true, false, true);
		progress.setOperationLimit(2);
		
		SAXParserFactory factory = SAXParserFactory.newInstance();
		SAXParser parser = factory.newSAXParser();
		
		long currentTime = System.currentTimeMillis();
		long projectSaveTime = currentTime + projectSaveInterval;
		
		for(WikiNamespace ns : project.listNamespaces())
		{
			progress.println("Processing namespace " + ns.getID() + " (" + ns.getName() + ")...");

			int totalPages = project.countPages(ns.getID());
			int currentPage = 0;

			progress.setProjectProgress(0);
			progress.setProjectLimit(totalPages);
			
			ArrayList<WikiPage> pages = project.listPages(ns.getID());
			
			if(pages == null)
			{
				continue;
			}
			
			for(WikiPage page : pages)
			{
				page.load(progress);
				
				currentPage++;

				if(page.hasImages())
				{
					ImageUploadVerifier handler = new ImageUploadVerifier(project, page);

					do
					{
						String request = project.getTargetURL() +
										 "api.php?format=xml&action=query&prop=imageinfo" +
										 "&iiprop=timestamp" +
										 "&iilimit=" + pagesPerRequest +
										 "&titles=" + Util.encodeURL(page.getTitle());

						if(handler.queryContinuePair != null)
						{
							request += "&" + handler.queryContinuePair;

							handler.queryContinuePair = null;
							handler.queryContinueFrom = null;
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
					
					int imagesUploaded = page.countUploadedImages();
					int imagesDownloaded = page.countDownloadedImages();
					
					page.unload(true);
					
					if(imagesUploaded != imagesDownloaded)
					{
						progress.println("[" + imagesUploaded + "/" + imagesDownloaded + "] " + page.getTitle());
					}
				}
				else
				{
					page.unload(false);
				}
				
				progress.setProjectProgress(currentPage);

				currentTime = System.currentTimeMillis();

				if(progress.isCancelled())
				{
					break;
				}
				else if(currentTime >= projectSaveTime)
				{
					System.gc();
					project.write(progress);
					projectSaveTime = currentTime + projectSaveInterval;
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
	protected static final int MODE_QUERY_CONTINUE = 2;
	
	protected Project project;
	protected WikiPage page;
	
	protected int mode = MODE_NULL;
	
	public String queryContinueFrom = null;
	public String queryContinuePair = null;
	
	public boolean parsed = false;
	
	public ImageUploadVerifier(Project project, WikiPage page)
	{
		this.project = project;
		this.page = page;
	}
	
	public void startElement(String uri, String localName, String qName, Attributes attributes)
	{
		if(qName.equals("page"))
		{
			String title = attributes.getValue("title");
			
			if(page.getTitle().equals(title))
			{
				for(WikiImage img : page.listImages())
				{
					img.setUploaded(false);
				}
				
				mode = MODE_PAGE;
			}
			
			parsed = true;
		}
		else if(mode == MODE_PAGE && qName.equals("ii"))
		{
			WikiImage img = page.getImage(attributes.getValue("timestamp"));
			
			if(img != null)
			{
				img.setUploaded(true);
			}
		}
		else if(qName.equals("query-continue"))
		{
			mode = MODE_QUERY_CONTINUE;
		}
		else if(mode == MODE_QUERY_CONTINUE && qName.equals("imageinfo"))
		{
			if(queryContinueFrom == null)
			{
				queryContinueFrom = attributes.getValue(0);
			}
			
			if(queryContinuePair != null)
			{
				queryContinuePair += "&";
			}
			else
			{
				queryContinuePair = "";
			}
			
			queryContinuePair += attributes.getQName(0) + "=" + Util.encodeURL(attributes.getValue(0));
		}
	}
	
	public void endElement(String uri, String localName, String qName)
	{
		if(qName.equals("page") || qName.equals("query-continue"))
		{
			mode = MODE_NULL;
		}
	}
}