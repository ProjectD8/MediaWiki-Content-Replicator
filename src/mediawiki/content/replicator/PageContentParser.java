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

import java.io.File;
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

public class PageContentParser extends DefaultHandler
{
	public static void getPagesContents(Project project, ProgressMonitor progress, int pagesPerRequest, long projectSaveInterval, boolean requestImageInfo) throws ParserConfigurationException, SAXException, IOException, TransformerException
	{
		progress.initProgress(true, true, true);
		progress.setOperationLimit(2);
		
		SAXParserFactory factory = SAXParserFactory.newInstance();
		SAXParser parser = factory.newSAXParser();
		
		File dir = new File((project.getBaseDir() + "wiki/").replace('/', File.separatorChar));
		dir.mkdirs();
		
		long currentTime = System.currentTimeMillis();
		long projectSaveTime = currentTime + projectSaveInterval;
		
		int totalPages = project.countPages();
		int currentPage = 0;
		
		progress.setProjectLimit(totalPages);
		
		for(WikiNamespace ns : project.listNamespaces())
		{
			ArrayList<WikiPage> pages = project.listPages(ns.getID());
			
			if(pages == null)
			{
				continue;
			}
			
			for(WikiPage page : pages)
			{
				page.load(progress);
				
				progress.setPageLimit(page.listRevisions().size());
				progress.setPageProgress(page.countDownloadedRevisions());
				
				currentPage++;
				// progress.print("[" + currentPage + "/" + totalPages + "]");

				if(page.isActual()) // firstRun && !p.requiresDownload())
				{
					// progress.println("[=] " + page.getTitle());
					page.unload(false);
					
					if(progress.isCancelled())
					{
						break;
					}
					else
					{
						progress.setProjectProgress(currentPage);
						continue;
					}
				}

				PageContentParser handler = new PageContentParser(project, page);

				String updateLimit = "";
				
				progress.print("[" + currentPage + "/" + totalPages + "]");

				switch(page.getDownloadStatus())
				{
					case WikiPage.REQUIRES_DOWNLOAD:
						progress.println("[+] " + page.getTitle());
						break;

					default:
					case WikiPage.PARTIALLY_DOWNLOADED:
						progress.println("[#] " + page.getTitle());
						break;

					case WikiPage.DOWNLOADED:
						progress.println("[*] " + page.getTitle());
						updateLimit = "&rvendid=" + page.getNewestRevisionID();
						break;
				}

				do
				{
					String request = project.getBaseURL() +
									 "api.php?format=xml&action=query" +
									 (requestImageInfo ? "&prop=revisions|imageinfo" : "&prop=revisions") +
									 "&rvprop=ids|timestamp|user|comment|content" +
									 "&iiprop=timestamp|user|comment|url|archivename" +
									 "&rvlimit=" + pagesPerRequest +
									 "&iilimit=" + pagesPerRequest +
									 "&pageids=" + page.getID() +
									 "&rvdir=older" + updateLimit;

					if(handler.queryContinuePair != null)
					{
						progress.println("... revision " + handler.queryContinueFrom);

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
							
							handler.parsed = false;
							handler.error = null;

							parser.parse(is, handler);
							progress.setOperationProgress(2);

							is.close();
							progress.setOperationProgress(0);

							if(handler.parsed)
							{
								break;
							}
							else if(handler.error != null)
							{
								throw handler.error;
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
								Thread.sleep(ex instanceof WikiException ? 500 : 5000);
							}
							catch(InterruptedException ie)
							{
							}
						}
					}
				}
				while(handler.queryContinuePair != null && !progress.isCancelled());

				boolean inflated = false;

				for(WikiRevision rv : page.listRevisions())
				{
					/*
					 * Текст обновляется только для новых.
					 * Существующие мы пропускаем в парсере.
					 */

					if(rv.isTouched() && rv.hasText()) // мы таки обновили текст
					{
						if(!inflated)
						{
							if(page.hasZipName())
							{
								page.inflate(new File(dir, page.getZipName().replace('/', File.separatorChar)), WikiPage.INFLATE_ALL);
							}

							inflated = true;
						}

						/*
						 * Вот этот setText() потребует затем вызова deflate().
						 * А перед вызовом deflate() обязательно должен идти inflate().
						 */

						rv.setText(rv.getText().replace("\r\n", "\n").replace("\n", "\r\n"));
					}

					rv.setTouched(false);
				}

				if(inflated)
				{
					String zipname = page.getZipName();
					page.deflate(new File(dir, zipname.replace('/', File.separatorChar)));
					page.setZipName(zipname);
				}

				page.setActual(true);
				page.unload(true);
				
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
	protected static final int MODE_REVISION = 2;
	protected static final int MODE_QUERY_CONTINUE = 3;
	
	protected Project project;
	protected WikiPage page;
	protected WikiRevision revision;
	
	protected StringBuilder buf;
	protected int mode = MODE_NULL;
	
	public String queryContinueFrom = null;
	public String queryContinuePair = null;
	
	public boolean parsed = false;
	public WikiException error = null;
	
	protected boolean hasRevisions;
	protected boolean hasImages;
	
	public PageContentParser(Project project, WikiPage page)
	{
		this.project = project;
		this.page = page;
		
		buf = new StringBuilder();
	}
	
	public void startElement(String uri, String localName, String qName, Attributes attributes)
	{
		if(qName.equals("page"))
		{
			int id = Integer.parseInt(attributes.getValue("pageid"));
			
			if(id == page.getID())
			{
				page.setMissing(attributes.getValue("missing") != null);
				
				if(!page.isMissing())
				{
					page.setTitle(attributes.getValue("title"));
					page.setNS(Integer.parseInt(attributes.getValue("ns")));
					
					project.invalidateIndex();
				}
				
				mode = MODE_PAGE;
			}
			
			parsed = true;
			
			hasRevisions = false;
			hasImages = false;
		}
		else if(mode == MODE_PAGE && qName.equals("revisions"))
		{
			hasRevisions = true;
		}
		else if(mode == MODE_PAGE && qName.equals("imageinfo"))
		{
			hasImages = true;
		}
		else if(mode == MODE_PAGE && qName.equals("rev"))
		{
			int id = Integer.parseInt(attributes.getValue("revid"));
			int parentid = Integer.parseInt(attributes.getValue("parentid"));
			String timestamp = attributes.getValue("timestamp");
			
			revision = page.getRevision(id);
			
			if(revision == null)
			{
				revision = new WikiRevision(id, parentid, timestamp);
				page.addRevision(revision);
			}
			
			if(!revision.hasEntryName())
			{
				revision.setUser(attributes.getValue("user"));
				revision.setAnonymous(attributes.getValue("anon") != null);
				revision.setComment(attributes.getValue("comment"));

				mode = MODE_REVISION;
			}
		}
		else if(mode == MODE_PAGE && qName.equals("ii"))
		{
			String timestamp = attributes.getValue("timestamp");
			
			WikiImage image = page.getImage(timestamp);
			
			if(image == null)
			{
				image = new WikiImage(timestamp);
				page.addImage(image);
			}
			
			image.setUser(attributes.getValue("user"));
			image.setComment(attributes.getValue("comment"));
			image.setArchiveName(attributes.getValue("archivename"));
			image.setURL(attributes.getValue("url"));
		}
		else if(qName.equals("query-continue"))
		{
			mode = MODE_QUERY_CONTINUE;
		}
		else if(mode == MODE_QUERY_CONTINUE && qName.equals("revisions"))
		{
			queryContinueFrom = attributes.getValue(0);
			
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
		else if(qName.equals("error"))
		{
			error = new WikiException(attributes.getValue("code"), attributes.getValue("info"));
		}
	}
	
	public void characters(char[] ch, int start, int length)
	{
		if(mode == MODE_REVISION)
		{
			buf.append(ch, start, length);
		}
	}
	
	public void endElement(String uri, String localName, String qName)
	{
		if(qName.equals("page") || qName.equals("query-continue"))
		{
			if(mode == MODE_PAGE && !(hasRevisions || hasImages))
			{
				page.setMissing(true);
			}
			
			mode = MODE_NULL;
		}
		else if(mode == MODE_REVISION && qName.equals("rev"))
		{
			revision.setText(buf.toString());
			revision.setTouched(true);
			buf.setLength(0);
			
			revision = null;
			
			mode = MODE_PAGE;
		}
	}
}