/* Copyright 2011 SpringSource.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package grails.plugin.reveng

/**
 * @author <a href='mailto:burt@burtbeckwith.com'>Burt Beckwith</a>
 */
class RevengRunner {

	void run(config, String appName) {

		Reenigne reenigne = new Reenigne()
		reenigne.grailsConfig = config.config
		reenigne.driverClass = config.driverClassName
		reenigne.password = config.password
		reenigne.username = config.username
		reenigne.url = config.url
		reenigne.dialect = config.dialect

		reenigne.packageName = config.packageName
		reenigne.destDir = new File(config.destDir)
		if (config.defaultSchema) {
			reenigne.defaultSchema = config.defaultSchema
		}
		if (config.defaultCatalog) {
			reenigne.defaultCatalog = config.defaultCatalog
		}
		reenigne.overwrite = config.overwriteExisting

		def strategy = reenigne.reverseEngineeringStrategy

		config.versionColumns.each { table, column -> strategy.addVersionColumn table, column }

		config.manyToManyTables.each { table -> strategy.addManyToManyTable table }

		config.manyToManyBelongsTos.each { manyTable, belongsTable -> strategy.setManyToManyBelongsTo manyTable, belongsTable }

		config.includeTables.each { table -> strategy.addIncludeTable table }

		config.includeTableRegexes.each { pattern -> strategy.addIncludeTableRegex pattern }

		config.includeTableAntPatterns.each { pattern -> strategy.addIncludeTableAntPattern pattern }

		config.excludeTables.each { table -> strategy.addExcludeTable table }

		config.excludeTableRegexes.each { pattern -> strategy.addExcludeTableRegex pattern }

		config.excludeTableAntPatterns.each { pattern -> strategy.addExcludeTableAntPattern pattern }

		config.excludeColumns.each { table, columns -> strategy.addExcludeColumns table, columns }

		config.excludeColumnRegexes.each { table, patterns -> strategy.addExcludeColumnRegexes table, patterns }

		config.excludeColumnAntPatterns.each { table, patterns -> strategy.addExcludeColumnAntPatterns table, patterns }

		config.mappedManyToManyTables.each { table -> strategy.addMappedManyToManyTable table }

		strategy.alwaysMapManyToManyTables = config.alwaysMapManyToManyTables

		reenigne.execute()
	}
}
