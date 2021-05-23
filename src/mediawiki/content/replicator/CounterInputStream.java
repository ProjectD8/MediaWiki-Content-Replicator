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

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.CRC32;

public class CounterInputStream extends FilterInputStream
{
	protected CRC32 crc = new CRC32();
	protected long count = 0;
	
	public CounterInputStream(InputStream in)
	{
		super(in);
	}
	
	public int read(byte[] b) throws IOException
	{
		return read(b, 0, b.length);
	}
	
	public int read(byte[] b, int off, int len) throws IOException
	{
		len = in.read(b, off, len);
		
		if(len > 0)
		{
			crc.update(b, off, len);
			count += len;
		}
		
		return len;
	}
	
	public int read() throws IOException
	{
		int b = in.read();
		
		if(b >= 0)
		{
			crc.update(b);
			count++;
		}
		
		return b;
	}
	
	public long getChecksum()
	{
		return crc.getValue();
	}
	
	public long getCount()
	{
		return count;
	}
}