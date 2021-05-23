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

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class WikiRename
{
	private int id;
	private String timestamp;
	
	private int sourceNS;
	private String sourceTitle;
	
	private int destNS;
	private String destTitle;
	
	private String user;
	private String comment;
	
	public WikiRename(int id, String timestamp)
	{
		this.id = id;
		this.timestamp = timestamp;
	}
	
	public WikiRename(DataInputStream dis, int version) throws IOException
	{
		read(dis, version);
	}
	
	public void write(DataOutputStream dos) throws IOException
	{
		dos.writeInt(id);
		Util.writeUTF(dos, timestamp);
		
		dos.writeInt(sourceNS);
		Util.writeUTF(dos, sourceTitle);
		
		dos.writeInt(destNS);
		Util.writeUTF(dos, destTitle);
		
		Util.writeUTF(dos, user);
		Util.writeUTF(dos, comment);
	}
	
	public void read(DataInputStream dis, int version) throws IOException
	{
		id = dis.readInt();
		timestamp = Util.readUTF(dis);
		
		sourceNS = dis.readInt();
		sourceTitle = Util.readUTF(dis);
		
		destNS = dis.readInt();
		destTitle = Util.readUTF(dis);
		
		user = Util.readUTF(dis);
		comment = Util.readUTF(dis);
	}

	public int getID()
	{
		return id;
	}

	public String getTimeStamp()
	{
		return timestamp;
	}
	
	public void setSource(int sourceNS, String sourceTitle)
	{
		this.sourceNS = sourceNS;
		this.sourceTitle = sourceTitle;
	}

	public int getSourceNS()
	{
		return sourceNS;
	}

	public String getSourceTitle()
	{
		return sourceTitle;
	}
	
	public void setDestination(int destNS, String destTitle)
	{
		this.destNS = destNS;
		this.destTitle = destTitle;
	}

	public int getDestNS()
	{
		return destNS;
	}

	public String getDestTitle()
	{
		return destTitle;
	}

	public String getUser()
	{
		return user;
	}

	public void setUser(String user)
	{
		this.user = user;
	}

	public String getComment()
	{
		return comment;
	}

	public void setComment(String comment)
	{
		this.comment = comment;
	}

	public int hashCode()
	{
		int hash = 3;
		hash = 17 * hash + this.id;
		return hash;
	}

	public boolean equals(Object obj)
	{
		if(obj == null)
		{
			return false;
		}
		if(getClass() != obj.getClass())
		{
			return false;
		}
		final WikiRename other = (WikiRename) obj;
		if(this.id != other.id)
		{
			return false;
		}
		return true;
	}
}
