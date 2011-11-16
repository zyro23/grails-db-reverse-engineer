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

import grails.util.BuildSettings
import grails.util.GrailsUtil

import org.apache.ivy.core.report.ResolveReport
import org.codehaus.groovy.grails.compiler.Grailsc
import org.codehaus.groovy.grails.resolve.IvyDependencyManager
import org.codehaus.groovy.util.ReleaseInfo

includeTargets << grailsScript('_GrailsBootstrap')

USAGE = 'grails db-reverse-engineer'

/**
 * @author <a href='mailto:burt@burtbeckwith.com'>Burt Beckwith</a>
 */
target(dbReverseEngineer: 'Reverse-engineers a database and creates domain classes') {
	depends packageApp, loadApp

	try {
		createConfig()

		def mergedConfig = buildMergedConfig()
		String configPath = writeConfig(mergedConfig)

		event('StatusUpdate', ["Starting database reverse engineering, connecting to '$mergedConfig.url' as '$mergedConfig.username' ..."])

		def jars = resolveJars(config.grails.plugin.reveng)
		File revengclasses = new File(buildSettings.projectTargetDir, 'revengclasses')
		revengclasses.deleteDir()
		revengclasses.mkdirs()

		ant.path(id: 'reveng.cp') { jars.each { pathelement(path: it) } }

		ant.taskdef(name: 'groovyc', classname: Grailsc.name)

		ant.groovyc(destdir: revengclasses.path, verbose: true, listfiles: true, classpathref: 'reveng.cp', includeAntRuntime: false) {
			javac(classpathref: 'reveng.cp', includeAntRuntime: false)
			src(path: "$dbReverseEngineerPluginDir/src/groovy")
		}

		ant.copy(todir: revengclasses.path) {
			fileset(dir: "$dbReverseEngineerPluginDir/src/java", excludes: '**/*.java')
		}

		try {
			ant.java(classname: 'grails.plugin.reveng.RevengRunner', fork: true,
			         failonerror: true, maxmemory: '512m', outputproperty: 'reveng.output') {
				arg(value: configPath)
				arg(value: metadata['app.name'])
				ant.classpath {
					path(refid: 'reveng.cp')
					pathelement(location: revengclasses.absolutePath)
				}
			}
		}
		catch (e) {
			event('StatusError', ["Error running forked reverse-engineer script: $e.message"])
		}
		println "\n${ant.project.properties['reveng.output']}\n"

		event('StatusUpdate', ['Finished database reverse engineering'])
	}
	catch (e) {
		GrailsUtil.deepSanitize e
		throw e
	}
}

private List<String> resolveJars(revengConfig) {

	def deps = [
		'org.hibernate:hibernate-core:3.3.1.GA',
		'org.hibernate:hibernate-commons-annotations:3.1.0.GA',
		'freemarker:freemarker:2.3.8',
		'org.hibernate:hibernate-tools:3.2.4.GA',
		'org.hibernate:jtidy:r8-20060801',
		'org.beanshell:bsh:2.0b4',
		'dom4j:dom4j:1.6.1',
		'commons-logging:commons-logging:1.1.1',
		'log4j:log4j:1.2.15',
		// need to use current Grails version of Groovy because of the serialization
		"org.codehaus.groovy:groovy-all:${ReleaseInfo.version}",
		'org.slf4j:slf4j-api:1.5.8',
		'org.slf4j:slf4j-log4j12:1.5.8',
		'org.springframework:spring-core:3.0.3.RELEASE',
		'org.grails:grails-bootstrap:1.3.3',
		'org.grails:grails-core:1.3.3']

	if (!revengConfig.jdbcDriverJarDep && !revengConfig.jdbcDriverJarPath) {
		event('StatusError', ["Neither grails.plugin.reveng.jdbcDriverJarDep or grails.plugin.reveng.jdbcDriverJarPath are set, so there's no JDBC driver configured; you'll most likely see an unrelated error"])
	}

	if (revengConfig.jdbcDriverJarDep) {
		deps << revengConfig.jdbcDriverJarDep
	}

	def manager = new IvyDependencyManager('reveng', '0.1', new BuildSettings())
	manager.parseDependencies {
		log revengConfig.ivyLogLevel ?: 'warn'
		repositories {
			mavenLocal()
			mavenCentral()
		}
		dependencies {
			compile(*deps) {
				transitive = false
			}
		}
	}

	ResolveReport report = manager.resolveDependencies()
	if (report.hasError()) {
		// TODO
		return null
	}

	def paths = []
	for (File file in report.allArtifactsReports.localFile) {
		if (file) paths << file.path
	}

	if (revengConfig.jdbcDriverJarPath) {
		paths << revengConfig.jdbcDriverJarPath
	}

	paths
}

// serialize the config to a file so it's available to the forked process
private String writeConfig(Map mergedConfig) {
	File file = File.createTempFile('reveng.config', '.ser')
	file.deleteOnExit()
	file.withObjectOutputStream { it.writeObject mergedConfig }
	file.path
}

private Map buildMergedConfig() {

	def mergedConfig = [:]

	def dsConfig = config.dataSource

	mergedConfig.driverClassName = dsConfig.driverClassName ?: 'org.h2.Driver'
	mergedConfig.password = dsConfig.password ?: ''
	mergedConfig.username = dsConfig.username ?: 'sa'
	mergedConfig.url = dsConfig.url ?: 'jdbc:h2:mem:testDB'
	if (dsConfig.dialect instanceof String) {
		mergedConfig.dialect = dsConfig.dialect
	}
	else if (dsConfig.dialect instanceof Class) {
		mergedConfig.dialect = dsConfig.dialect.name
	}

	def revengConfig = config.grails.plugin.reveng
	mergedConfig.packageName = revengConfig.packageName ?: metadata['app.name']
	mergedConfig.destDir = new File(basedir, revengConfig.destDir ?: 'grails-app/domain').canonicalPath
	if (revengConfig.defaultSchema) {
		mergedConfig.defaultSchema = revengConfig.defaultSchema
	}
	if (revengConfig.defaultCatalog) {
		mergedConfig.defaultCatalog = revengConfig.defaultCatalog
	}
	if (revengConfig.overwriteExisting instanceof Boolean) {
		mergedConfig.overwriteExisting = revengConfig.overwriteExisting
	}
	else {
		mergedConfig.overwriteExisting = true
	}

	if (revengConfig.alwaysMapManyToManyTables instanceof Boolean) {
		mergedConfig.alwaysMapManyToManyTables = revengConfig.alwaysMapManyToManyTables
	}
	else {
		mergedConfig.alwaysMapManyToManyTables = false
	}

	for (String name in ['versionColumns', 'manyToManyTables', 'manyToManyBelongsTos',
	                     'includeTables', 'includeTableRegexes', 'includeTableAntPatterns',
	                     'excludeTables', 'excludeTableRegexes', 'excludeTableAntPatterns',
	                     'excludeColumns', 'excludeColumnRegexes', 'excludeColumnAntPatterns',
	                     'mappedManyToManyTables']) {
		mergedConfig[name] = revengConfig[name]
	}

	mergedConfig
}

setDefaultTarget dbReverseEngineer
