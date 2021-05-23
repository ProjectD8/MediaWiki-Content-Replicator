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

public class WikiNamespace
{
	private int id;
	private String name;
	
	private boolean included;
	
	public WikiNamespace(int id)
	{
		this.id = id;
		
		name = "";
		included = true;
	}
	
	public WikiNamespace(Project project, DataInputStream dis, int version, ProgressMonitor progress) throws IOException
	{
		read(project, dis, version, progress);
	}
	
	public void write(DataOutputStream dos) throws IOException
	{
		dos.writeInt(id);
		dos.writeUTF(name);
		
		dos.writeBoolean(included);
	}
	
	public void read(Project project, DataInputStream dis, int version, ProgressMonitor progress) throws IOException
	{
		id = dis.readInt();
		name = dis.readUTF();
		
		included = dis.readBoolean();
		
		if(version < 7)
		{
			int count = dis.readInt();

			for(int i = 0; i < count; i++)
			{
				project.addPage(new WikiPage(dis, version, progress));
			}
		}
	}
	
	public int getID()
	{
		return id;
	}
	
	public void setName(String name)
	{
		this.name = name;
	}
	
	public String getName()
	{
		if(name != null && !name.isEmpty())
		{
			return name;
		}
		else
		{
			return "_default";
		}
	}
	
	public void setIncluded(boolean included)
	{
		this.included = included;
	}
	
	public boolean isIncluded()
	{
		return included;
	}
}