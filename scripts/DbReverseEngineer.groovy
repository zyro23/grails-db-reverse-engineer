/* Copyright 2010-2011 SpringSource.
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

import grails.util.GrailsUtil

includeTargets << grailsScript('_GrailsBootstrap')

USAGE = 'grails db-reverse-engineer'

/**
 * @author <a href='mailto:burt@burtbeckwith.com'>Burt Beckwith</a>
 */
target(dbReverseEngineer: 'Reverse-engineers a database and creates domain classes') {
	depends packageApp, loadApp

	try {
		createConfig()
		def dsConfig = config.dataSource

		def reenigne = classLoader.loadClass('grails.plugin.reveng.Reenigne').newInstance()
		reenigne.grailsConfig = config
		reenigne.driverClass = dsConfig.driverClassName ?: 'org.h2.Driver'
		reenigne.password = dsConfig.password ?: ''
		reenigne.username = dsConfig.username ?: 'sa'
		reenigne.url = dsConfig.url ?: 'jdbc:h2:mem:testDB'
		if (dsConfig.dialect instanceof String) {
			reenigne.dialect = dsConfig.dialect
		}
		else if (dsConfig.dialect instanceof Class) {
			reenigne.dialect = dsConfig.dialect.name
		}

		def revengConfig = config.grails.plugin.reveng
		reenigne.packageName = revengConfig.packageName ?: metadata['app.name']
		reenigne.destDir = new File(basedir, revengConfig.destDir ?: 'grails-app/domain')
		if (revengConfig.defaultSchema) {
			reenigne.defaultSchema = revengConfig.defaultSchema
		}
		if (revengConfig.defaultCatalog) {
			reenigne.defaultCatalog = revengConfig.defaultCatalog
		}
		if (revengConfig.overwriteExisting instanceof Boolean) {
			reenigne.overwrite = revengConfig.overwriteExisting
		}

		def strategy = reenigne.reverseEngineeringStrategy

		revengConfig.versionColumns.each { table, column -> strategy.addVersionColumn table, column }

		revengConfig.manyToManyTables.each { table -> strategy.addManyToManyTable table }

		revengConfig.manyToManyBelongsTos.each { manyTable, belongsTable -> strategy.setManyToManyBelongsTo manyTable, belongsTable }

		revengConfig.includeTables.each { table -> strategy.addIncludeTable table }

		revengConfig.includeTableRegexes.each { pattern -> strategy.addIncludeTableRegex pattern }

		revengConfig.includeTableAntPatterns.each { pattern -> strategy.addIncludeTableAntPattern pattern }

		revengConfig.excludeTables.each { table -> strategy.addExcludeTable table }

		revengConfig.excludeTableRegexes.each { pattern -> strategy.addExcludeTableRegex pattern }

		revengConfig.excludeTableAntPatterns.each { pattern -> strategy.addExcludeTableAntPattern pattern }

		revengConfig.excludeColumns.each { table, columns -> strategy.addExcludeColumns table, columns }

		revengConfig.excludeColumnRegexes.each { table, patterns -> strategy.addExcludeColumnRegexes table, patterns }

		revengConfig.excludeColumnAntPatterns.each { table, patterns -> strategy.addExcludeColumnAntPatterns table, patterns }

		revengConfig.mappedManyToManyTables.each { table -> strategy.addMappedManyToManyTable table }

		if (revengConfig.alwaysMapManyToManyTables instanceof Boolean) {
			strategy.alwaysMapManyToManyTables = revengConfig.alwaysMapManyToManyTables
		}

		event('StatusUpdate', ["Starting database reverse engineering, connecting to '$reenigne.url' as '$reenigne.username' ..."])
		reenigne.execute()
		event('StatusUpdate', ['Finished database reverse engineering'])
	}
	catch (e) {
		GrailsUtil.deepSanitize e
		throw e
	}
}

setDefaultTarget dbReverseEngineer
