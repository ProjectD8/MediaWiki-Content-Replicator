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

import java.io.BufferedInputStream;
import java.io.IOException;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.TransformerException;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

public class DumpVerifier extends DefaultHandler
{
	public static void verifyDump(CounterInputStream counter, ProgressMonitor progress) throws ParserConfigurationException, SAXException, IOException, TransformerException
	{
		progress.initProgress(true, true, true);
		
		SAXParserFactory factory = SAXParserFactory.newInstance();
		SAXParser parser = factory.newSAXParser();
		
		DumpVerifier handler = new DumpVerifier(progress);
		
		parser.parse(new BufferedInputStream(counter, Util.BUFFER_SIZE), handler);
		
		progress.println();
		progress.println("Parsed " + handler.numPages + " pages");
		progress.println("Total of " + counter.getCount() + " bytes read, CRC32 is " + Long.toHexString(counter.getChecksum() & 0xFFFFFFFFL).toUpperCase());
		progress.println("         " + handler.numErrors + " errors, " + handler.numFatalErrors + " fatal errors, " + handler.numWarnings + " warnings");
		progress.println();
	}
	
	protected static final int MODE_NULL = 0;
	protected static final int MODE_PAGE = 1;
	protected static final int MODE_PAGE_TITLE = 2;
	
	protected StringBuilder buf = new StringBuilder();
	protected int mode = MODE_NULL;
	
	protected ProgressMonitor progress;
	
	public int numPages = 0;
	public int numRevisions = 0;
	
	public int numErrors = 0;
	public int numFatalErrors = 0;
	public int numWarnings = 0;

	public DumpVerifier(ProgressMonitor progress)
	{
		this.progress = progress;
	}
	
	public void startElement(String uri, String localName, String qName, Attributes attributes)
	{
		if(mode == MODE_NULL)
		{
			if(qName.equals("page"))
			{
				numPages++;
				mode = MODE_PAGE;
				
				progress.setProjectLimit(numPages);
				progress.setProjectProgress(numPages);
			}
		}
		else if(mode == MODE_PAGE)
		{
			if(qName.equals("title"))
			{
				mode = MODE_PAGE_TITLE;
			}
			else if(qName.equals("revision"))
			{
				
			}
		}
	}
	
	public void characters(char[] ch, int start, int length)
	{
		if(mode == MODE_PAGE_TITLE)
		{
			buf.append(ch, start, length);
		}
	}
	
	public void endElement(String uri, String localName, String qName)
	{
		if(mode == MODE_PAGE_TITLE)
		{
			if(qName.equals("title"))
			{
				progress.println("[" + numErrors + "/" + numFatalErrors + "/" + numWarnings + "][" + numPages + "] " + buf.toString());
				buf.setLength(0);
				
				mode = MODE_PAGE;
			}
		}
		else if(mode == MODE_PAGE)
		{
			if(qName.equals("page"))
			{
				mode = MODE_NULL;
			}
		}
	}
	
	public void error(SAXParseException exception)
	{
		numErrors++;
		progress.println("Error: " + exception.toString());
	}
	
	public void fatalError(SAXParseException exception)
	{
		numFatalErrors++;
		progress.println("Fatal error: " + exception.toString());
	}
	
	public void warning(SAXParseException exception)
	{
		numWarnings++;
		progress.println("Warning: " + exception.toString());
	}
}