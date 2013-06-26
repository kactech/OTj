/*******************************************************************************
 *              OTj
 * Low-level client-side library for Open Transactions in Java
 * 
 * Copyright (C) 2013 by Piotr Kopeć (kactech)
 * 
 * EMAIL: pepe.kopec@gmail.com
 * 
 * BITCOIN: 1ESADvST7ubsFce7aEi2B6c6E2tYd4mHQp
 * 
 * OFFICIAL PROJECT PAGE: https://github.com/kactech/OTj
 * 
 * -------------------------------------------------------
 * 
 * LICENSE:
 * This program is free software: you can redistribute it
 * and/or modify it under the terms of the GNU Affero
 * General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your
 * option) any later version.
 * 
 * ADDITIONAL PERMISSION under the GNU Affero GPL version 3
 * section 7: If you modify this Program, or
 * any covered work, by linking or combining it with other
 * code, such other code is not for that reason alone subject
 * to any of the requirements of the GNU Affero GPL version 3.
 * (==> This means if you are only using the OTj, then you
 * don't have to open-source your code--only your changes to
 * OTj itself must be open source. Similar to
 * LGPLv3, except it applies to software-as-a-service, not
 * just to distributing binaries.)
 * Anyone using my library is given additional permission
 * to link their software with any BSD-licensed code.
 * 
 * -----------------------------------------------------
 * 
 * You should have received a copy of the GNU Affero General
 * Public License along with this program. If not, see:
 * http://www.gnu.org/licenses/
 * 
 * If you would like to use this software outside of the free
 * software license, please contact Piotr Kopeć.
 * 
 * DISCLAIMER:
 * This program is distributed in the hope that it will be
 * useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR
 * PURPOSE. See the GNU Affero General Public License for
 * more details.
 ******************************************************************************/
package com.kactech.otj.examples;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.google.gson.reflect.TypeToken;
import com.kactech.otj.Engines;
import com.kactech.otj.Utils;
import com.thoughtworks.xstream.XStream;

public class ExamplesUtils {
	public static void serializeJava(Path path, Object obj) {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		try {
			ObjectOutputStream oos = new ObjectOutputStream(bos);
			oos.writeObject(obj);
			oos.flush();
			oos.close();
			Utils.writeDirs(path, bos.toByteArray());
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public static Object deserializeJava(Path path) {
		Object obj;
		try {
			ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(Files.readAllBytes(path)));
			obj = ois.readObject();
			ois.close();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		return obj;
	}

	public static void serializeXStream(Path path, Object obj) {
		try {
			Utils.writeDirs(path, new XStream().toXML(obj));
		} catch (IOException e) {
			// test environment- just hide it
			throw new RuntimeException(e);
		}
	}

	public static Object deserializeXStream(Path path) {
		try {
			return new XStream().fromXML(Utils.read(path));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private static List<SampleAccount> sampleAccounts;

	public static List<SampleAccount> getSampleAccounts() {
		if (sampleAccounts == null)
			sampleAccounts = Engines.gson.fromJson(new InputStreamReader(Engines.class
					.getResourceAsStream("/com/kactech/otj/examples/sample-accounts.json")),
					new TypeToken<List<SampleAccount>>() {
					}.getType());
		return sampleAccounts;
	}
}
