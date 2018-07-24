package autofe.db.util;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;

import autofe.db.model.database.AbstractAttribute;
import autofe.db.model.database.AggregationFunction;
import autofe.db.model.database.Database;
import autofe.db.model.database.Table;
import autofe.db.model.relation.AbstractRelationship;
import autofe.db.model.relation.BackwardRelationship;
import autofe.db.model.relation.ForwardRelationship;

public class DBUtils {

	private static Logger LOG = LoggerFactory.getLogger(DBUtils.class);

	public static Table getTargetTable(Database db) {
		for (Table t : db.getTables()) {
			if (t.isTarget()) {
				return t;
			}
		}
		return null;
	}

	public static Set<ForwardRelationship> getForwardsFor(Table table, Database db) {
		Set<ForwardRelationship> toReturn = new HashSet<>();
		for (ForwardRelationship forwardRelationship : db.getForwards()) {
			forwardRelationship.setContext(db);
			if (forwardRelationship.getFrom().equals(table)) {
				toReturn.add(forwardRelationship);
			}
		}
		LOG.info("There are {} forward relationships from table {}", toReturn.size(), table.getName());
		return toReturn;
	}

	public static Set<BackwardRelationship> getBackwardsFor(Table table, Database db) {
		Set<BackwardRelationship> toReturn = new HashSet<>();
		// TODO: Tables are not equal here
		// TODO: Problem: In den Relations werden andere Objekte referenziert als in den
		// Tabellen
		for (BackwardRelationship backwardRelationship : db.getBackwards()) {
			backwardRelationship.setContext(db);
			if (backwardRelationship.getFrom().equals(table)) {
				toReturn.add(backwardRelationship);
			}
		}
		LOG.info("There are {} backward relationships from table {}", toReturn.size(), table.getName());
		return toReturn;
	}

	public static void serializeToFile(Database db, String path) {
		Gson gson = initGson();
		try {
			FileWriter fw = new FileWriter(path);
			gson.toJson(db, fw);
			fw.flush();
			fw.close();
		} catch (JsonIOException | IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public static Database deserializeFromFile(String path) {
		Database db = null;
		Gson gson = initGson();
		try {
			db = gson.fromJson(new FileReader(path), Database.class);
		} catch (JsonSyntaxException | JsonIOException | FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return db;
	}

	public static String serializeToString(Database db) {
		System.out.println(db);
		Gson gson = initGson();
		try {
			return gson.toJson(db);
		} catch (JsonIOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}

	public static Database deserializeFromString(String serialized) {
		Database db = null;
		Gson gson = initGson();
		try {
			db = gson.fromJson(serialized, Database.class);
		} catch (JsonSyntaxException | JsonIOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return db;
	}

	private static Gson initGson() {
		Gson gson = new GsonBuilder().registerTypeAdapter(AbstractAttribute.class, new InterfaceAdapter<>())
				.registerTypeAdapter(AbstractRelationship.class, new InterfaceAdapter<>()).create();
		return gson;
	}

	public static Database clone(Database db) {
		String serialized = serializeToString(db);
		return deserializeFromString(serialized);
	}

	public static Table getTableByName(String name, Database db) {
		for (Table t : db.getTables()) {
			if (t.getName().equals(name)) {
				return t;
			}
		}
		return null;
	}

	public static AbstractAttribute getAttributeByName(String name, Table table) {
		for (AbstractAttribute a : table.getColumns()) {
			if (a.getName().equals(name)) {
				return a;
			}
		}
		return null;
	}

	public static String getAggregatedAttributeName(AggregationFunction aggregationFunction, String toTableName,
			String toBeAggregatedName) {
		return aggregationFunction.name() + "(" + toTableName + "." + toBeAggregatedName + ")";
	}

}
