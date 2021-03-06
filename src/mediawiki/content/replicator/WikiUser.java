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

public class WikiUser
{
	private String name;
	private boolean exists;

	public WikiUser(String name)
	{
		this.name = name;
	}
	
	public WikiUser(DataInputStream dis, int version) throws IOException
	{
		read(dis, version);
	}
	
	public void write(DataOutputStream dos) throws IOException
	{
		dos.writeUTF(name);
		dos.writeBoolean(exists);
	}
	
	public void read(DataInputStream dis, int version) throws IOException
	{
		name = dis.readUTF();
		
		if(version >= 9)
		{
			exists = dis.readBoolean();
		}
		else
		{
			exists = false;
		}
	}

	public String getName()
	{
		return name;
	}

	public void setName(String name)
	{
		this.name = name;
	}
	
	public String getPassword()
	{
		return Util.md5(Util.noEmpty(name, "")).toUpperCase();
	}
	
	public boolean exists()
	{
		return exists;
	}
	
	public void setExists(boolean exists)
	{
		this.exists = exists;
	}

	public int hashCode()
	{
		int hash = 7;
		hash = 89 * hash + (this.name != null ? this.name.hashCode() : 0);
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
		final WikiUser other = (WikiUser) obj;
		if((this.name == null) ? (other.name != null) : !this.name.equals(other.name))
		{
			return false;
		}
		return true;
	}
}