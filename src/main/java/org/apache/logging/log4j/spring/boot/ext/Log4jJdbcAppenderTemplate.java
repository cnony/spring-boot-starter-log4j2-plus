/*
 * Copyright (c) 2017, vindell (https://github.com/vindell).
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
/**
 * 
 */
package org.apache.logging.log4j.spring.boot.ext;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.sql.DataSource;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.db.ColumnMapping;
import org.apache.logging.log4j.core.appender.db.jdbc.ColumnConfig;
import org.apache.logging.log4j.core.appender.db.jdbc.ConnectionSource;
import org.apache.logging.log4j.core.appender.db.jdbc.JdbcAppender;
import org.apache.logging.log4j.core.filter.MarkerFilter;
import org.apache.logging.log4j.spring.boot.Log4jJdbcProperties;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

/**
 * TODO
 * @author <a href="https://github.com/vindell">vindell</a>
 */
public class Log4jJdbcAppenderTemplate implements InitializingBean {

	private DataSource dataSource;
	private Log4jJdbcProperties jdbcProperties;
	private static final ConcurrentMap<Marker, JdbcAppender> COMPLIED_FORMAT = new ConcurrentHashMap<Marker, JdbcAppender>();
	
	/**
	 * Sets the datasource to use.
	 * @param dataSource The datasource to use.
	 */
	public void setDataSource(DataSource dataSource) {
		this.dataSource = dataSource;
	}

	/**
	 * Sets the properties to use.
	 * @param jdbcProperties The properties to use.
	 */
	public void setProperties(Log4jJdbcProperties jdbcProperties) {
		this.jdbcProperties = jdbcProperties;
	}
	
	public static JdbcAppender getJdbcAppender(Marker marker) {
		JdbcAppender ret = COMPLIED_FORMAT.get(marker);
		if (ret != null) {
			return ret;
		}
		return null;
 	}
	
	public JdbcAppender newJdbcAppender(final org.apache.logging.log4j.core.config.Configuration config, Log4jJdbcAppenderProperties properties) {
		
		List<Log4jJdbcColumnConfig> columnMappingList = properties.getColumnMappings();
		
		ColumnMapping[] columnMappings = {};
		ColumnConfig[] columnConfigs = new ColumnConfig[columnMappingList.size()];
		for (int i = 0; i < columnMappingList.size(); i++) {
			Log4jJdbcColumnConfig column = columnMappingList.get(i);
			if(column != null) {
				columnConfigs[i] = column.toColumnConfig(config);
			}
		}
		
		// 配置Marker过滤器(标记过滤器)
		MarkerFilter filter = MarkerFilter.createFilter(properties.getMarker(), Filter.Result.ACCEPT,
				Filter.Result.DENY);
		// build ConnectionSource Impl
		ConnectionSource connectionSource = new Log4jJdbcConnectionSource(dataSource);
		// build JdbcAppender
		JdbcAppender appender = JdbcAppender.newBuilder()
				.setBufferSize(properties.getBufferSize())
				.setConfiguration(config)
				.setColumnConfigs(columnConfigs)
				.setColumnMappings(columnMappings)
				.setConnectionSource(connectionSource)
				.setTableName(properties.getTableName())
				.withName(properties.getAppender())
				.withIgnoreExceptions(properties.isIgnoreExceptions())
				.withFilter(filter)
				.build();
		
		config.addAppender(appender);
		
		appender.start();
				
		return appender;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.springframework.beans.factory.InitializingBean#afterPropertiesSet()
	 */
	@Override
	public void afterPropertiesSet() throws Exception {

		List<Log4jJdbcAppenderProperties> jdbcAppenders = jdbcProperties.getAppenders();
		Assert.notEmpty(jdbcAppenders, "Need to specify at least one JdbcAppender Properties.");
		
		final LoggerContext ctx = (LoggerContext) LogManager.getContext(jdbcProperties.isCurrentContext());
		final org.apache.logging.log4j.core.config.Configuration config = ctx.getConfiguration();
		
		for (Log4jJdbcAppenderProperties properties : jdbcAppenders) {
			
			Marker marker = MarkerFactory.getMarker(properties.getMarker()); 
			JdbcAppender ret = COMPLIED_FORMAT.get(marker);
			if (ret != null || CollectionUtils.isEmpty(properties.getColumnMappings())) {
				continue;
			}
			
			final Logger interLogger = ctx.getLogger(properties.getLogger());
			JdbcAppender appender = this.newJdbcAppender(config, properties);
			interLogger.addAppender(appender);
			
			COMPLIED_FORMAT.put(marker, appender);
			
		}
		
		ctx.updateLoggers();
		
	}

}
