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
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;

public class WikiRevision implements Comparable<WikiRevision>
{
	public static final HashMap<String, String> TEXT_ATTRIBUTES;
	
	static
	{
		TEXT_ATTRIBUTES = new HashMap();
		TEXT_ATTRIBUTES.put("xml:space", "preserve");
	}
	
	private WikiPage page;
	
	private int id;
	private int parentid;
	
	private String user;
	private boolean anonymous;
	
	private String timestamp;
	private String comment;
	
	private String text;
	private int encodedTextLength = -1;
	private String entryname;
	private boolean useDefaultEntryName;
	
	private boolean touched;
	private boolean uploaded;

	public WikiRevision(int id, int parentid, String timestamp)
	{
		this.id = id;
		this.parentid = parentid;
		this.timestamp = timestamp;
		
		touched = false;
		uploaded = false;
	}
	
	public WikiRevision(DataInput dis, int version) throws IOException
	{
		read(dis, version);
	}
	
	public void write(DataOutput dos) throws IOException
	{
		dos.writeInt(id);
		dos.writeInt(parentid);
		
		Util.writeUTF(dos, user);
		dos.writeBoolean(anonymous);
		
		Util.writeUTF(dos, timestamp);
		Util.writeUTF(dos, comment);
		
		Util.writeUTF(dos, null /* filename */);
		Util.writeUTF(dos, hasEntryName() ? getEntryName() : null);
		dos.writeBoolean(useDefaultEntryName);
		
		dos.writeBoolean(uploaded);
	}
	
	public void read(DataInput dis, int version) throws IOException
	{
		id = dis.readInt();
		parentid = dis.readInt();
		
		user = Util.readUTF(dis);
		anonymous = dis.readBoolean();
		
		timestamp = Util.readUTF(dis);
		comment = Util.readUTF(dis);
		
		/* filename = */ Util.readUTF(dis);
		
		entryname = (version >= 2) ? Util.readUTF(dis) : null;
		useDefaultEntryName = (version >= 11) ? dis.readBoolean() : false;
		
//		if(useDefaultEntryName)
//		{
//			entryname = null;
//		}
		
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

	public int getID()
	{
		return id;
	}

	public int getParentID()
	{
		return parentid;
	}
	
	public boolean hasParent()
	{
		return parentid > 0;
	}

	public String getUser()
	{
		return user;
	}

	public void setUser(String user)
	{
		this.user = user;
	}

	public boolean isAnonymous()
	{
		return anonymous;
	}

	public void setAnonymous(boolean anonymous)
	{
		this.anonymous = anonymous;
	}

	public String getTimestamp()
	{
		return timestamp;
	}

	public String getComment()
	{
		return comment != null ? comment : "";
	}

	public void setComment(String comment)
	{
		this.comment = comment;
	}
	
	public boolean hasText()
	{
		return text != null;
	}

	public String getText()
	{
		return text;
	}

	public void setText(String text)
	{
		this.text = text;
		this.encodedTextLength = -1;
	}

	public int getEncodedTextLength()
	{
		if(encodedTextLength < 0 && text != null)
		{
			try
			{
				encodedTextLength = text.getBytes("UTF-8").length;
			}
			catch(UnsupportedEncodingException ex)
			{
			}
		}
		
		return encodedTextLength;
	}

	public void setEncodedTextLength(int encodedTextLength)
	{
		this.encodedTextLength = encodedTextLength;
	}

	public boolean isTouched()
	{
		return touched;
	}

	public void setTouched(boolean touched)
	{
		this.touched = touched;
	}
	
//	public boolean hasFileName()
//	{
//		return filename != null;
//	}
//
//	public String getFileName()
//	{
//		if(filename != null)
//		{
//			return filename;
//		}
//		
//		return Util.validateFileName(page.getTitle()) + "." + Integer.toString(page.getID()) + "/" + Integer.toString(id) + ".txt";
//	}
//
//	public void setFileName(String filename)
//	{
//		this.filename = filename;
//	}
	
	public boolean hasEntryName()
	{
		return entryname != null || useDefaultEntryName;
	}
	
	public String getDefaultEntryName()
	{
		return timestamp + " -- " + Integer.toString(id) + " -- " + Util.validateFileName(Util.noEmpty(user, "anonymous")) + " -- " + Util.validateFileName(Util.noEmpty(comment, "empty")) + ".txt";
	}

	public String getEntryName()
	{
		if(entryname != null)
		{
			return entryname;
		}
		
		return getDefaultEntryName();
	}

	public void setEntryName(String name)
	{
		if(getDefaultEntryName().equals(name))
		{
			entryname = null;
			useDefaultEntryName = true;
		}
		else
		{
			entryname = name;
			useDefaultEntryName = false;
		}
	}
	
	public boolean needsRefactoring()
	{
		return hasEntryName() && !getDefaultEntryName().equals(getEntryName());
	}

	public boolean isUploaded()
	{
		return uploaded;
	}

	public void setUploaded(boolean uploaded)
	{
		this.uploaded = uploaded;
	}

	public int compareTo(WikiRevision other)
	{
		return timestamp.compareTo(other.timestamp);
	}
	
	public void dump(XMLStringBuilder xml)
	{
		xml.openTag("revision");

		xml.append("id", Integer.toString(id));

		if(parentid > 0)
		{
			xml.append("parentid", Integer.toString(parentid));
		}

		if(timestamp != null)
		{
			xml.append("timestamp", timestamp);
		}

		if(!anonymous && user != null)
		{
			xml.openTag("contributor");
			xml.append("username", user);
			xml.closeTag();
		}

		if(comment != null)
		{
			xml.append("comment", comment);
		}
		
		if(text != null)
		{
			xml.append("model", "wikitext");
			xml.append("format", "text/x-wiki");
			
			TEXT_ATTRIBUTES.put("bytes", Integer.toString(getEncodedTextLength()));
			
			xml.append("text", text, TEXT_ATTRIBUTES);
		}
		
		xml.closeTag();
	}
}