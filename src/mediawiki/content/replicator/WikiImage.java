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

import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.regex.Pattern;
import org.apache.commons.codec.binary.Base64OutputStream;

public class WikiImage implements Comparable<WikiImage>
{
	public static final HashMap<String, String> CONTENT_ATTRIBUTES;
	
	static
	{
		CONTENT_ATTRIBUTES = new HashMap();
		CONTENT_ATTRIBUTES.put("encoding", "base64");
	}
	
	public static final Pattern ARCHIVE_NAME_PATTERN = Pattern.compile("[0-9]{14}!.*");
	
	private WikiPage page;
	
	private String timestamp;
	private String user;
	private String comment;
	private String archivename;
	
	private String url;
	private String filename;
	
	private boolean uploaded;
	
//	private InputStream stream;

	public WikiImage(String timestamp)
	{
		this.timestamp = timestamp;
	}
	
	public WikiImage(DataInput dis, int version) throws IOException
	{
		read(dis, version);
	}
	
	public void write(DataOutput dos) throws IOException
	{
		Util.writeUTF(dos, timestamp);
		Util.writeUTF(dos, user);
		Util.writeUTF(dos, comment);
		Util.writeUTF(dos, archivename);
		
		Util.writeUTF(dos, url);
		Util.writeUTF(dos, filename);
		
		dos.writeBoolean(uploaded);
	}
	
	public void read(DataInput dis, int version) throws IOException
	{
		timestamp = Util.readUTF(dis);
		user = Util.readUTF(dis);
		comment = Util.readUTF(dis);
		archivename = (version >= 4) ? Util.readUTF(dis) : null;
		
		url = Util.readUTF(dis);
		filename = Util.readUTF(dis);
		
		uploaded = dis.readBoolean();
	}

	public WikiPage getPage()
	{
		return page;
	}

	public void setPage(WikiPage page)
	{
		this.page = page;
	}

	public String getTimestamp()
	{
		return timestamp;
	}

	public void setTimestamp(String timestamp)
	{
		this.timestamp = timestamp;
	}

	public String getUser()
	{
		return user;
	}

	public void setUser(String user)
	{
		this.user = user;
	}
	
	public boolean hasComment()
	{
		return comment != null;
	}

	public String getComment()
	{
		return comment != null ? comment : "";
	}

	public void setComment(String comment)
	{
		this.comment = comment;
	}

	public String getArchiveName()
	{
		return archivename;
	}

	public void setArchiveName(String archivename)
	{
		this.archivename = archivename;
	}
	
//	public void verifyArchiveName()
//	{
//		if(hasFileName())
//		{
//			File file = new File(page.getProject().getImageDir(), getFileName());
//			
//			if(file.exists())
//			{
//				archivename = null;
//			}
//			else
//			{
//				file = new File(page.getProject().getImageArchiveDir(), getFileName());
//				
//				if(file.exists())
//				{
//					archivename = getFileName();
//				}
//			}
//		}
//	}
	
	public boolean isArchived()
	{
		return archivename != null;
	}
	
	public boolean hasURL()
	{
		return url != null;
	}

	public String getURL()
	{
		return url;
	}

	public void setURL(String url)
	{
		this.url = url;
	}
	
	public String getFullyQualifiedName()
	{
		return page.getTitle() + " @ " + timestamp;
	}
	
	public boolean hasFileName()
	{
		return filename != null;
	}
	
	public String getDefaultFileName()
	{
		String name = url.substring(url.lastIndexOf('/') + 1);
		
		try
		{
			name = URLDecoder.decode(name, "UTF-8");
		}
		catch(UnsupportedEncodingException ex)
		{
		}
		
		if(ARCHIVE_NAME_PATTERN.matcher(name).matches())
		{
			int index = name.lastIndexOf('.');
			
			if(index >= 0)
			{
				name = name.substring(15, index) + "." + name.substring(0, 14) + name.substring(index);
			}
			else
			{
				name = name.substring(15) + "." + name.substring(0, 14);
			}
		}
		
		name = Util.validateFileName(name);
		
		if(!name.isEmpty())
		{
			return name.substring(0, 1) + "/" + name;
		}
		else
		{
			return name;
		}
	}
	
	public String getDefaultArchiveName()
	{
		String name = url.substring(url.lastIndexOf('/') + 1);
		
		try
		{
			name = URLDecoder.decode(name, "UTF-8");
		}
		catch(UnsupportedEncodingException ex)
		{
		}
		
		return name;
	}

	public String getFileName()
	{
		if(filename != null)
		{
			return filename;
		}
		
		return getDefaultFileName();
	}

	public void setFileName(String filename)
	{
		this.filename = filename;
	}
	
	public boolean needsRefactoring()
	{
		return hasFileName() && !getDefaultFileName().equals(getFileName());
	}
	
//	public boolean hasOpenStream()
//	{
//		return stream != null;
//	}
//	
//	public InputStream getStream()
//	{
//		return stream;
//	}
//	
//	public InputStream openStream(File imageDir, File archiveDir) throws IOException
//	{
//		if(hasFileName())
//		{
//			File file = new File(imageDir, getFileName());
//
//			if(file.isFile())
//			{
//				return stream = new FileInputStream(file);
//			}
//
//			file = new File(archiveDir, getFileName());
//
//			if(file.isFile())
//			{
//				return stream = new FileInputStream(file);
//			}
//		}
//		
//		return null;
//	}
//	
//	public void closeStream()
//	{
//		if(stream != null)
//		{
//			try
//			{
//				stream.close();
//			}
//			catch(IOException ioe)
//			{
//			}
//			
//			stream = null;
//		}
//	}

	public boolean isUploaded()
	{
		return uploaded;
	}

	public void setUploaded(boolean uploaded)
	{
		this.uploaded = uploaded;
	}

	public int compareTo(WikiImage other)
	{
		return timestamp.compareTo(other.timestamp);
	}
	
	public void dump(XMLStringBuilder xml, boolean embed, ProgressMonitor progress) throws IOException
	{
		progress.setOperationProgress(0);
		
		xml.openTag("upload");

		if(timestamp != null)
		{
			xml.append("timestamp", timestamp);
		}

		if(user != null)
		{
			xml.openTag("contributor");
			xml.append("username", user);
			xml.closeTag();
		}

		if(comment != null)
		{
			xml.append("comment", comment);
		}
		
		xml.append("filename", page.getTitleWithoutNamespace());
		
		if(isArchived())
		{
			xml.append("archivename", getArchiveName());
		}
		
		if(hasURL())
		{
			xml.append("src", getURL());
		}
		
		if(hasFileName())
		{
			File file = new File(page.getProject().getImageDir(), getFileName());

			if(!file.isFile())
			{
				file = new File(page.getProject().getImageArchiveDir(), getFileName());
			}
			
			if(file.isFile())
			{
				xml.append("size", Long.toString(file.length()));
				
				if(embed)
				{
					xml.openTag("contents", CONTENT_ATTRIBUTES);
					xml.flush();

					FileInputStream in = new FileInputStream(file);
					Base64OutputStream out = new Base64OutputStream(xml.getOutputStream());

					byte[] buf = new byte[Util.BUFFER_SIZE];
					int len;

					progress.setOperationLimit((int)file.length());

					while(true)
					{
						len = in.read(buf);

						if(len > 0)
						{
							out.write(buf, 0, len);
							progress.progressOperation(len);
						}
						else if(len < 0)
						{
							break;
						}
					}

					in.close();

					out.flush();
					xml.getWriter().append("\r\n");

					xml.closeTag();
				}
				else
				{
					if(isArchived())
					{
						xml.append("rel", Project.IMAGES_ARCHIVE_DIR + getFileName());
					}
					else
					{
						xml.append("rel", Project.IMAGES_ACTUAL_DIR + getFileName());
					}
				}
			}
		}
		
		xml.closeTag();
	}
}