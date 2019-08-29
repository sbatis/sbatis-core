package com.obatis.core.sql;

import com.obatis.config.request.PageParam;
import com.obatis.config.request.RequestConstant;
import com.obatis.config.request.RequestParam;
import com.obatis.core.constant.type.FilterEnum;
import com.obatis.core.constant.type.OrderEnum;
import com.obatis.core.constant.type.SqlHandleEnum;
import com.obatis.core.convert.BeanCacheConvert;
import com.obatis.core.exception.HandleException;
import com.obatis.core.result.ResultInfoOutput;
import com.obatis.core.sql.mysql.HandleOrderMethod;
import com.obatis.validate.ValidateTool;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 数据库操作 sql 封装操作类，除使用直接拼装 sql 外，其余数据库操作全部使用这个类提供的属性进行操作
 * @author HuangLongPu
 */
public class QueryProvider {

	protected static final String JOIN_AND_EXPRESS = " and ";
	protected static final String JOIN_OR_EXPRESS = " or ";

	protected static AbstractOrder abstractOrder = new HandleOrderMethod();

	private static final Map<Integer, OrderEnum> ORDER_TYPE_MAP = new HashMap<>();
	private Object updateObj;

	static {
		// 加载排序方式和值
		ORDER_TYPE_MAP.put(RequestConstant.ORDER_ASC, OrderEnum.ORDER_ASC);
		ORDER_TYPE_MAP.put(RequestConstant.ORDER_DESC, OrderEnum.ORDER_DESC);
	}

	private int pageNumber = RequestConstant.DEFAULT_PAGE;
	private int pageSize = RequestConstant.DEFAULT_ROWS;

	private List<Object[]> fields;
	private List<Object[]> filters;
	private List<Object[]> orders;
	private List<Object[]> groups;
	private List<QueryProvider> orProviders;
	private Map<String, String> notFields;
	private List<Object[]> leftJoinProviders;
	private String joinTableName;
	private List<Object[]> onFilters;

	public int getPageNumber() {
		return pageNumber;
	}

	public void setPageNumber(int pageNumber) {
		this.pageNumber = pageNumber;
	}

	public int getPageSize() {
		return pageSize;
	}

	public void setPageSize(int pageSize) {
		this.pageSize = pageSize;
	}

	public void setPage(PageParam pageParam) {
		this.setPageNumber(pageParam.getPage());
		this.setPageSize(pageParam.getRows());

		String sort = pageParam.getSort();
		if (!ValidateTool.isEmpty(sort)) {
			this.setOrder(sort, ORDER_TYPE_MAP.get(pageParam.getOrder()));
		}
	}

	public List<Object[]> getFields() {
		return fields;
	}

	public List<Object[]> getFilters() {
		return filters;
	}

	public List<Object[]> getOrders() {
		return orders;
	}

	public List<Object[]> getGroups() {
		return groups;
	}

	public List<QueryProvider> getOrProviders() {
		return orProviders;
	}

	public Map<String, String> getNotFields() {
		return notFields;
	}

	public List<Object[]> getLeftJoinProviders() {
		return leftJoinProviders;
	}

	protected String getJoinTableName() {
		return joinTableName;
	}

	public List<Object[]> getOnFilters() {
		return onFilters;
	}

	/**
	 * 设置连接查询时 QueryProvider 属性表名，如果只是简单常规单表查询，即使设置了也无效。 目前主要支持 left join
	 * @param joinTableName
	 */
	public void setJoinTableName(String joinTableName) {
		if (ValidateTool.isEmpty(joinTableName)) {
			throw new HandleException("error: joinTableName is null");
		}
		this.joinTableName = joinTableName;
	}

	/**
	 * 添加字段方法，接收一个参数，此方法主要用于查询 传入的值表示为要查询的字段名称
	 * @param fieldName
	 * @throws HandleException
	 */
	public void set(String fieldName) throws HandleException {
		this.set(fieldName, null);
	}

	/**
	 * 添加字段方法，接收两个参数，此方法主要用于查询(select)或者修改(update) 此方法用于查询或者修改
	 * 用于查询时，第一个参数为要查询的字段名称，第二个参数可为null或者为要查询的别名，类似sql语句中的as name
	 * 用于修改时，第一个参数为要修改的字段名称，第二个为修改后的值
	 * @param fieldName
	 * @param value
	 * @throws HandleException
	 */
	public void set(String fieldName, Object value) throws HandleException {
		this.addValue(fieldName, SqlHandleEnum.HANDLE_DEFAULT, value);
	}

    /**
     * 用于select查询
     * @param columns
     * @throws HandleException
     */
    public void select(String...columns) throws HandleException {
        for (String column : columns) {
            if(ValidateTool.isEmpty(column)) {
                throw new HandleException("error: column is null");
            }
            this.set(column);
        }
    }

	/**
	 * 实现累加，比如money = money + 20类似的SQL语句; fieldName 表示要操作的字段名称,value 表示要操作的值
	 * @param fieldName
	 * @param value
	 */
	public void addUp(String fieldName, Object value) {
		this.addValue(fieldName, SqlHandleEnum.HANDLE_UP, value);
	}

	/**
	 * 实现累加，比如money = money - 20类似的SQL语句; fieldName 表示要操作的字段名称,value 表示要操作的值
	 * @param fieldName
	 * @param value
	 */
	public void addReduce(String fieldName, Object value) {
		this.addValue(fieldName, SqlHandleEnum.HANDLE_REDUCE, value);
	}
	
	/**
	 * count 统计函数 >> count(1)，默认别名为 count。
	 * 该方法已过期，由 selectCount 替代
	 */
	@Deprecated
	public void addCount() {
		this.selectCount("count");
	}

	/**
	 * count 统计函数 >> count(1)，默认别名为 count。
	 */
	public void selectCount() {
		this.selectCount("count");
	}
	
	/**
	 * count 统计函数 >> count(1) as 'aliasName'。
	 * 该方法已过期，由 selectCount 替代
	 * @param aliasName
	 */
	@Deprecated
	public void addCount(String  aliasName) {
		this.addValue("", SqlHandleEnum.HANDLE_COUNT, aliasName);
	}

	/**
	 * count 统计函数 >> count(1) as 'aliasName'。
	 * @param aliasName
	 */
	public void selectCount(String  aliasName) {
		this.addValue("", SqlHandleEnum.HANDLE_COUNT, aliasName);
	}
	
	/**
	 * distinct 去重函数 >> distinct 'fieldName'，默认别名为 'fieldName'。
	 * 方法已过期，由 selectCountDistinct 替代
	 * @param fieldName
	 */
	@Deprecated
	public void addCountDistinct(String fieldName) {
		this.selectCountDistinct(fieldName);
	}

	/**
	 * count与distinct 去重函数联合使用 >> count distinct 'fieldName'，默认别名为 'fieldName'
	 * @param fieldName
	 */
	public void selectCountDistinct(String fieldName) {
		this.selectCountDistinct(fieldName, fieldName);
	}
	
	/**
	 * distinct 去重函数 >> distinct 'fieldName' as 'aliasName'。
	 * 方法已过期，由 selectCountDistinct 替代
	 * @param fieldName
	 * @param aliasName
	 */
	@Deprecated
	public void addCountDistinct(String fieldName, String aliasName) {
		this.selectCountDistinct(fieldName, aliasName);
	}

	/**
	 * count与distinct 去重函数联合使用 >> count distinct 'fieldName' as 'aliasName'
	 * @param fieldName
	 * @param aliasName
	 */
	public void selectCountDistinct(String fieldName, String aliasName) {
		if(ValidateTool.isEmpty(fieldName)) {
			throw new HandleException("error: field is null");
		}
		if(ValidateTool.isEmpty(aliasName)) {
			aliasName = fieldName;
		}
		this.addValue(fieldName, SqlHandleEnum.HANDLE_COUNT, aliasName);
	}
	
	/**
	 * sum 求和函数 >> sum('fieldName')，默认别名为 'fieldName'
	 * 方法已过期，由 selectSum 替代
	 * @param fieldName
	 */
	@Deprecated
	public void addSum(String fieldName) {
		this.selectSum(fieldName);
	}

	/**
	 * sum 求和函数 >> sum('fieldName')，默认别名为 'fieldName'
	 * @param fieldName
	 */
	public void selectSum(String fieldName) {
		this.selectSum(fieldName, fieldName);
	}
	
	/**
	 * sum 求和函数 >> sum('fieldName') as 'aliasName'
	 * 方法已过期，由 selectSum 替代
	 * @param fieldName
	 * @param aliasName
	 */
	@Deprecated
	public void addSum(String fieldName, String  aliasName) {
		this.selectSum(fieldName, aliasName);
	}

	/**
	 * sum 求和函数 >> sum('fieldName') as 'aliasName'
	 * @param fieldName
	 * @param aliasName
	 */
	public void selectSum(String fieldName, String  aliasName) {
		this.addValue(fieldName, SqlHandleEnum.HANDLE_SUM, aliasName);
	}
	
	/**
	 * min 最小值函数 >> min('fieldName')，默认别名为 'fieldName'。
	 * 方法已过期，由 selectMin 替代
	 * @param fieldName
	 */
	@Deprecated
	public void addMin(String fieldName) {
		this.selectMin(fieldName);
	}

	/**
	 * min 最小值函数 >> min('fieldName')，默认别名为 'fieldName'
	 * @param fieldName
	 */
	public void selectMin(String fieldName) {
		this.selectMin(fieldName, fieldName);
	}
	
	/**
	 * min 最小值函数 >> min('fieldName') as 'aliasName'。
	 * 方法已过期，由 selectMin 替代
	 * @param fieldName
	 * @param aliasName
	 */
	@Deprecated
	public void addMin(String fieldName, String  aliasName) {
		this.selectMin(fieldName, aliasName);
	}

	/**
	 * min 最小值函数 >> min('fieldName') as 'aliasName'
	 * @param fieldName
	 * @param aliasName
	 */
	public void selectMin(String fieldName, String  aliasName) {
		if(ValidateTool.isEmpty(fieldName)) {
			throw new HandleException("error: field is null");
		}
		if(ValidateTool.isEmpty(aliasName)) {
			aliasName = fieldName;
		}
		this.addValue(fieldName, SqlHandleEnum.HANDLE_MIN, aliasName);
	}
	
	/**
	 * max 最大值函数 >> max('fieldName')，默认别名为 'fieldName'。
	 * 方法已过期，由 selectMax 替代
	 * @param fieldName
	 */
	@Deprecated
	public void addMax(String fieldName) {
		this.selectMax(fieldName);
	}

	/**
	 * max 最大值函数 >> max('fieldName')，默认别名为 'fieldName'
	 * @param fieldName
	 */
	public void selectMax(String fieldName) {
		this.selectMax(fieldName, fieldName);
	}
	
	/**
	 * max 最大值函数 >> max('fieldName') as 'aliasName'。
	 * 方法已过期，由 selectMax 替代
	 * @param fieldName    字段名
	 * @param aliasName    别名
	 */
	@Deprecated
	public void addMax(String fieldName, String  aliasName) {
		this.selectMax(fieldName, aliasName);
	}

	/**
	 * max 最大值函数 >> max('fieldName') as 'aliasName'
	 * @param fieldName
	 * @param aliasName
	 */
	public void selectMax(String fieldName, String  aliasName) {
		if(ValidateTool.isEmpty(fieldName)) {
			throw new HandleException("error: field is null");
		}
		if(ValidateTool.isEmpty(aliasName)) {
			aliasName = fieldName;
		}
		this.addValue(fieldName, SqlHandleEnum.HANDLE_MAX, aliasName);
	}
	
	/**
	 * avg 平均值函数 >> avg('fieldName')，默认别名为 'fieldName'。
	 * 方法已过期，由 selectAvg 替代
	 * @param fieldName    字段名
	 */
	@Deprecated
	public void addAvg(String fieldName) {
		this.selectAvg(fieldName);
	}

	/**
	 * avg 平均值函数 >> avg('fieldName')，默认别名为 'fieldName'
	 * @param fieldName
	 */
	public void selectAvg(String fieldName) {
		this.selectAvg(fieldName, fieldName);
	}
	
	/**
	 * avg 平均值函数 >> avg('fieldName') as 'aliasName'。
	 * 方法已过期，由 selectAvg 替代
	 * @param fieldName    字段名
	 * @param aliasName    别名
	 */
	@Deprecated
	public void addAvg(String fieldName, String  aliasName) {
		this.selectAvg(fieldName, aliasName);
	}

	/**
	 * avg 平均值函数 >> avg('fieldName') as 'aliasName'
	 * @param fieldName
	 * @param aliasName
	 */
	public void selectAvg(String fieldName, String  aliasName) {
		if(ValidateTool.isEmpty(fieldName)) {
			throw new HandleException("error: field is null");
		}
		if(ValidateTool.isEmpty(aliasName)) {
			aliasName = fieldName;
		}
		this.addValue(fieldName, SqlHandleEnum.HANDLE_AVG, aliasName);
	}
	
	/**
	 * 表达式函数，非聚合函数时使用，如需聚合，直接使用提供的聚合函数方法即可，同等原理。
	 * 方法已过期，由 selectExp 替代
	 * @param fieldName
	 */
	@Deprecated
	public void addExp(String fieldName) {
		this.selectExp(fieldName);
	}

	/**
	 * 表达式函数，非聚合函数时使用，如需聚合，直接使用提供的聚合函数方法即可，同等原理
	 * @param fieldName
	 */
	public void selectExp(String fieldName) {
		this.selectExp(fieldName, null);
	}
	
	/**
	 * 表达式函数，非聚合函数时使用，如需聚合，直接使用提供的聚合函数方法即可，同等原理。
	 * 方法已过期，由 selectExp 替代
	 * @param fieldName
	 * @param aliasName
	 */
	@Deprecated
	public void addExp(String fieldName, String  aliasName) {
		this.selectExp(fieldName, aliasName);
	}

	/**
	 * 表达式函数，非聚合函数时使用，如需聚合，直接使用提供的聚合函数方法即可，同等原理
	 * @param fieldName
	 * @param aliasName
	 */
	public void selectExp(String fieldName, String  aliasName) {
		if(ValidateTool.isEmpty(fieldName)) {
			throw new HandleException("error: field is null");
		}
		if(ValidateTool.isEmpty(aliasName)) {
			aliasName = "exp_value";
		}
		this.addValue(fieldName, SqlHandleEnum.HANDLE_EXP, aliasName);
	}

	/**
	 * 针对日期进行 format 处理，fieldName 默认为别名
	 * @param fieldName
	 * @param pattern
	 */
	public void selectDateFormat(String fieldName, String pattern) {
		this.selectDateFormat(fieldName, pattern, fieldName);
	}

	/**
	 * 针对日期进行 format 处理
	 * @param fieldName
	 * @param pattern
	 * @param aliasName
	 */
	public void selectDateFormat(String fieldName, String pattern, String aliasName) {
		if(ValidateTool.isEmpty(fieldName)) {
			throw new HandleException("error: field is null");
		}
		if(ValidateTool.isEmpty(pattern)) {
			throw new HandleException("error: pattern is null");
		}
		if(ValidateTool.isEmpty(aliasName)) {
			aliasName = "exp_value";
		}
		this.addValue(fieldName, SqlHandleEnum.HANDLE_DATE_FORMAT, aliasName, pattern);
	}

	/**
	 * 设置表达式属性
	 * @param fieldName
	 * @param fieldType
	 * @param value
	 */
	private void addValue(String fieldName, SqlHandleEnum fieldType, Object value) {
		this.addValue(fieldName, fieldType, value, null);
	}

	private void addValue(String fieldName, SqlHandleEnum fieldType, Object value, String pattern) {
//		if (ValidateTool.isEmpty(fieldName) && !SqlHandleEnum.HANDLE_COUNT.equals(fieldType)) {
//			throw new HandleException("error: field is null");
//		}
		if (this.fields == null) {
			this.fields = new ArrayList<>();
		}
		if(ValidateTool.isEmpty(pattern)) {
			Object[] obj = { fieldName, fieldType, value };
			this.fields.add(obj);
		} else {
			Object[] obj = { fieldName, fieldType, value, pattern};
			this.fields.add(obj);
		}

	}

	/**
	 * 添加不需要查询的字段，主要针对实体泛型返回的查询中，如果字段被加入，则会在 SQL 中过滤。
	 * @param fieldName
	 */
	public void setNotField(String fieldName) {
		if (ValidateTool.isEmpty(fieldName)) {
			throw new HandleException("error: field is null");
		}
		if (this.notFields == null) {
			this.notFields = new HashMap<>();
		}
		this.notFields.put(fieldName, fieldName);
	}

	/**
	 * 添加查询条件，where后的字段;
	 * 参数分别为字段名称，比如name。条件类型，比如=，具体的值参考QueryParam的FILTER开头的常量值
	 * @param filterName
	 * @param filterType
	 * @param value
	 */
	private void andFilter(String filterName, FilterEnum filterType, Object value) {
		this.addFilter(filterName, filterType, value, JOIN_AND_EXPRESS);
	}

	private void andFilter(String filterName, FilterEnum filterType, Object value, String pattern) {
		this.addFilter(filterName, filterType, value, JOIN_AND_EXPRESS, pattern);
	}

	/**
	 * 体现为 left join on 的连接查询条件
	 * 参数分别为字段名称，比如name。条件类型，比如=，具体的值参考QueryParam的FILTER开头的常量值
	 * @param filterName
	 * @param filterType
	 * @param value
	 */
	private void andOnFilter(String filterName, FilterEnum filterType, Object value) {
		this.addOnFilter(filterName, filterType, value, JOIN_AND_EXPRESS);
	}

	/**
	 * 体现为 left join on 的连接查询条件
	 * 参数分别为字段名称，比如name。条件类型，比如=，具体的值参考QueryParam的FILTER开头的常量值
	 * @param filterName
	 * @param filterType
	 * @param value
	 */
	private void andOnFilter(String filterName, FilterEnum filterType, Object value, String pattern) {
		this.addOnFilter(filterName, filterType, value, JOIN_AND_EXPRESS, pattern);
	}

	/**
	 * 设置条件
	 * @param filterName
	 * @param filterType
	 * @param value
	 * @param joinType
	 */
	private void addFilter(String filterName, FilterEnum filterType, Object value, String joinType) {
		this.addFilter(filterName, filterType, value, joinType, null);
	}

	private void addFilter(String filterName, FilterEnum filterType, Object value, String joinType, String pattern) {
		if (ValidateTool.isEmpty(filterName)) {
			throw new HandleException("error: filter field is null");
		} else if (!FilterEnum.IS_NULL.equals(filterType) && !FilterEnum.IS_NOT_NULL.equals(filterType) && null == value) {
			throw new HandleException("error: filter value is null");
		}

		if (this.filters == null) {
			this.filters = new ArrayList<>();
		} else {
			this.checkFilter(this.filters, filterName, filterType, value, joinType);
		}
		if(ValidateTool.isEmpty(pattern)) {
			Object[] obj = {filterName, filterType, value, joinType};
			this.filters.add(obj);
		} else {
			Object[] obj = {filterName, filterType, value, joinType, pattern};
			this.filters.add(obj);
		}
	}

	private void checkFilter(List<Object[]> filterList, String filterName, FilterEnum filterType, Object value, String joinType) {
		for (int i = 0, j = filterList.size(); i < j; i++) {
			Object[] filter = filterList.get(i);
			if(filter[0].toString().equals(filterName) && filterType.equals(filter[1]) && joinType.equals(filter[3])) {
				if(FilterEnum.IS_NULL.equals(filterType) || FilterEnum.IS_NOT_NULL.equals(filterType)) {
					break;
				} else if (value.equals(filter[2])) {
					break;
				} else {
					filterList.remove(i);
				}
			}
		}
	}

	/**
	 * 体现为 left join on 的连接查询条件
	 * 设置查询条件，可以传入定义的类型
	 * @param filterName
	 * @param filterType
	 * @param value
	 * @param joinType
	 */
	private void addOnFilter(String filterName, FilterEnum filterType, Object value, String joinType) {
		this.addOnFilter(filterName, filterType, value, joinType, null);
	}

	private void addOnFilter(String filterName, FilterEnum filterType, Object value, String joinType, String pattern) {
		if (ValidateTool.isEmpty(filterName)) {
			throw new HandleException("error: on filter field is null");
		} else if (!FilterEnum.IS_NULL.equals(filterType) && !FilterEnum.IS_NOT_NULL.equals(filterType) && null == value) {
			throw new HandleException("error: on filter value is null");
		}
		if (this.onFilters == null) {
			this.onFilters = new ArrayList<>();
		} else {
			this.checkFilter(this.onFilters, filterName, filterType, value, joinType);
		}

		if(ValidateTool.isEmpty(pattern)) {
			Object[] obj = {filterName, filterType, value, joinType};
			this.onFilters.add(obj);
		} else {
			Object[] obj = {filterName, filterType, value, joinType, pattern};
			this.onFilters.add(obj);
		}
	}

	/**
	 * 设置or 查询条件数据
	 * @param filterName
	 * @param filterType
	 * @param value
	 */
	private void or(String filterName, FilterEnum filterType, Object value) {
		this.addFilter(filterName, filterType, value, JOIN_OR_EXPRESS);
	}

	/**
	 * 设置or 查询条件数据，针对时间格式化
	 * @param filterName
	 * @param filterType
	 * @param value
	 */
	private void or(String filterName, FilterEnum filterType, Object value, String pattern) {
		this.addFilter(filterName, filterType, value, JOIN_OR_EXPRESS, pattern);
	}

	/**
	 * 设置连接查询 on 拼接的 or 条件
	 * @param filterName
	 * @param filterType
	 * @param value
	 */
	private void onOr(String filterName, FilterEnum filterType, Object value) {
		this.addOnFilter(filterName, filterType, value, JOIN_OR_EXPRESS);
	}

	private void onOr(String filterName, FilterEnum filterType, Object value, String pattern) {
		this.addOnFilter(filterName, filterType, value, JOIN_OR_EXPRESS, pattern);
	}

	/**
	 * 增加 and 查询条件，模糊查询, like
	 * @param filterName
	 * @param value
	 */
	public void like(String filterName, Object value) {
		this.andFilter(filterName, FilterEnum.LIKE, value);
	}

	/**
	 * 增加连接查询 on 连接的 like 模糊查询
	 * @param filterName
	 * @param value
	 */
	public void onLike(String filterName, Object value) {
		this.andOnFilter(filterName, FilterEnum.LIKE, value);
	}

	/**
	 * 增加 or 查询条件，模糊查询, like
	 * @param filterName
	 * @param value
	 */
	public void orLike(String filterName, Object value) {
		this.or(filterName, FilterEnum.LIKE, value);
	}

	/**
	 * 增加连接查询on 连接的 or 关系的模糊查询 like
	 * @param filterName
	 * @param value
	 */
	public void onOrLike(String filterName, Object value) {
		this.onOr(filterName, FilterEnum.LIKE, value);
	}

	/**
	 * 增加 and 查询条件，左模糊查询, like
	 * @param filterName
	 * @param value
	 */
	public void leftLike(String filterName, Object value) {
		this.andFilter(filterName, FilterEnum.LEFT_LIKE, value);
	}

	/**
	 * 增加连接查询 and查询条件的左模糊查询 like
	 * @param filterName
	 * @param value
	 */
	public void onLeftLike(String filterName, Object value) {
		this.andOnFilter(filterName, FilterEnum.LEFT_LIKE, value);
	}

	/**
	 * 增加 or 查询条件，左模糊查询, like
	 * @param filterName
	 * @param value
	 */
	public void orLeftLike(String filterName, Object value) {
		this.or(filterName, FilterEnum.LEFT_LIKE, value);
	}

	/**
	 * 增加连接查询 or 查询条件左模糊查询， like
	 * @param filterName
	 * @param value
	 */
	public void onOrLeftLike(String filterName, Object value) {
		this.onOr(filterName, FilterEnum.LEFT_LIKE, value);
	}

	/**
	 * 增加 and 查询条件，右模糊查询, like
	 * @param filterName
	 * @param value
	 */
	public void rightLike(String filterName, Object value) {
		this.andFilter(filterName, FilterEnum.RIGHT_LIKE, value);
	}

	/**
	 * 增加连接查询 and 查询条件的右模糊查询，like
	 * @param filterName
	 * @param value
	 */
	public void onRightLike(String filterName, Object value) {
		this.andOnFilter(filterName, FilterEnum.RIGHT_LIKE, value);
	}

	/**
	 * 增加 or 查询条件，右模糊查询, like
	 * @param filterName
	 * @param value
	 */
	public void orRightLike(String filterName, Object value) {
		this.or(filterName, FilterEnum.RIGHT_LIKE, value);
	}

	/**
	 * 增加连接查询 or查询条件的右模糊查询， like
	 * @param filterName
	 * @param value
	 */
	public void onOrRightLike(String filterName, Object value) {
		this.onOr(filterName, FilterEnum.RIGHT_LIKE, value);
	}

	/**
	 * 增加 and 查询条件，等于查询，=
	 * @param filterName
	 * @param value
	 */
	public void equals(String filterName, Object value) {
		this.andFilter(filterName, FilterEnum.EQUAL, value);
	}

	/**
	 * 增加 and 查询条件，等于查询，=，针对时间格式化使用
	 * @param filterName
	 * @param value
	 * @param pattern
	 */
	public void equalsDateFormat(String filterName, Object value, String pattern) {
		this.andFilter(filterName, FilterEnum.EQUAL_DATE_FORMAT, value, pattern);
	}

	/**
	 * 增加 and 字段比较查询，等于查询，=，例如 a = b，a和b均为数据库字段
	 * @param filterName
	 * @param fieldName
	 */
	public void equalsField(String filterName, String fieldName) {
		this.andFilter(filterName, FilterEnum.EQUAL_FIELD, fieldName);
	}

	/**
	 * 增加连接查询且为and关系查询条件
	 * @param filterName
	 * @param value
	 */
	public void onEquals(String filterName, Object value) {
		this.andOnFilter(filterName, FilterEnum.EQUAL, value);
	}

	/**
	 * 增加连接查询且为and关系查询条件，针对时间格式化使用
	 * @param filterName
	 * @param value
	 */
	public void onEqualsDateFormat(String filterName, Object value, String pattern) {
		this.andOnFilter(filterName, FilterEnum.EQUAL_DATE_FORMAT, value, pattern);
	}

	/**
	 * 增加连接查询且为and关系的字段值相等的查询条件，例如 a = b，a和b均为数据库字段
	 * @param filterName
	 * @param fieldName
	 */
	public void onEqualsField(String filterName, String fieldName) {
		this.andOnFilter(filterName, FilterEnum.EQUAL_FIELD, fieldName);
	}

	/**
	 * 增加 or 查询条件，等于查询，=
	 * @param filterName
	 * @param value
	 */
	public void orEquals(String filterName, Object value) {
		this.or(filterName, FilterEnum.EQUAL, value);
	}

	/**
	 * 增加 or 查询条件，等于查询，= ， 针对时间格式化
	 * @param filterName
	 * @param value
	 */
	public void orEqualsDateFormat(String filterName, Object value, String pattern) {
		this.or(filterName, FilterEnum.EQUAL_DATE_FORMAT, value, pattern);
	}

	/**
	 * 增加 or 关系的字段值相等的查询条件，例如 a = b，a和b均为数据库字段
	 * @param filterName
	 * @param fieldName
	 */
	public void orEqualsField(String filterName, String fieldName) {
		this.or(filterName, FilterEnum.EQUAL_FIELD, fieldName);
	}

	/**
	 * 增加连接查询 or 关系的查询条件，等于查询，=
	 * @param filterName
	 * @param value
	 */
	public void onOrEquals(String filterName, Object value) {
		this.onOr(filterName, FilterEnum.EQUAL, value);
	}

	/**
	 * 增加连接查询 or 关系的查询条件，等于查询，=，针对时间格式化查询条件
	 * @param filterName
	 * @param value
	 */
	public void onOrEqualsDateFormat(String filterName, Object value, String pattern) {
		this.onOr(filterName, FilterEnum.EQUAL_DATE_FORMAT, value, pattern);
	}

	/**
	 * 增加连接查询 or 关系的字段值相等的查询条件，例如 a = b，a和b均为数据库字段
	 * @param filterName
	 * @param fieldName
	 */
	public void onOrEqualsField(String filterName, String fieldName) {
		this.onOr(filterName, FilterEnum.EQUAL_FIELD, fieldName);
	}

	/**
	 * 增加 and 查询条件，大于查询，>
	 * @param filterName
	 * @param value
	 */
	public void greaterThan(String filterName, Object value) {
		this.andFilter(filterName, FilterEnum.GREATER_THAN, value);
	}

	/**
	 * 增加 and 查询条件，大于查询，>， 针对时间格式化查询条件
	 * @param filterName
	 * @param value
	 */
	public void greaterThanDateFormat(String filterName, Object value, String pattern) {
		this.andFilter(filterName, FilterEnum.GREATER_THAN_DATE_FORMAT, value, pattern);
	}

	/**
	 * 增加 and 字段比较查询条件，大于查询，>，例如 a > b，a和b均为数据库字段
	 * @param filterName
	 * @param fieldName
	 */
	public void greaterThanField(String filterName, String fieldName) {
		this.andFilter(filterName, FilterEnum.GREATER_THAN_FIELD, fieldName);
	}

	/**
	 * 增加连接查询 and 查询条件，大于查询，>
	 * @param filterName
	 * @param value
	 */
	public void onGreaterThan(String filterName, Object value) {
		this.andOnFilter(filterName, FilterEnum.GREATER_THAN, value);
	}

	/**
	 * 增加连接查询 and 查询条件，大于查询，>，针对时间格式化查询条件
	 * @param filterName
	 * @param value
	 */
	public void onGreaterThanDateFormat(String filterName, Object value, String pattern) {
		this.andOnFilter(filterName, FilterEnum.GREATER_THAN_DATE_FORMAT, value, pattern);
	}

	/**
	 * 增加 and 查询条件，字段大于查询，>，例如 a > b，a和b均为数据库字段
	 * @param filterName
	 * @param fieldName
	 */
	public void onGreaterThanField(String filterName, String fieldName) {
		this.andOnFilter(filterName, FilterEnum.GREATER_THAN_FIELD, fieldName);
	}

	/**
	 * 增加 or 查询条件，大于查询，>
	 * @param filterName
	 * @param value
	 */
	public void orGreaterThan(String filterName, Object value) {
		this.or(filterName, FilterEnum.GREATER_THAN, value);
	}

	/**
	 * 增加 or 查询条件，大于查询，>，针对时间格式化查询条件
	 * @param filterName
	 * @param value
	 */
	public void orGreaterThanDateFormat(String filterName, Object value, String pattern) {
		this.or(filterName, FilterEnum.GREATER_THAN_DATE_FORMAT, value, pattern);
	}

	/**
	 * 增加 or 字段比较查询条件，大于查询，>，例如 a > b，a和b均为数据库字段
	 * @param filterName
	 * @param fieldName
	 */
	public void orGreaterThanField(String filterName, String fieldName) {
		this.or(filterName, FilterEnum.GREATER_THAN_FIELD, fieldName);
	}

	/**
	 * 增加连接查询 or 查询条件，大于查询，>
	 * @param filterName
	 * @param value
	 */
	public void onOrGreaterThan(String filterName, Object value) {
		this.onOr(filterName, FilterEnum.GREATER_THAN, value);
	}

	/**
	 * 增加连接查询 or 查询条件，大于查询，>，针对时间格式化查询条件
	 * @param filterName
	 * @param value
	 */
	public void onOrGreaterThanDateFormat(String filterName, Object value, String pattern) {
		this.onOr(filterName, FilterEnum.GREATER_THAN_DATE_FORMAT, value, pattern);
	}

	/**
	 * 增加连接查询 or 关系字段大于查询条件，大于查询，>，例如 a > b，a和b均为数据库字段
	 * @param filterName
	 * @param fieldName
	 */
	public void onOrGreaterThanField(String filterName, String fieldName) {
		this.onOr(filterName, FilterEnum.GREATER_THAN_FIELD, fieldName);
	}

	/**
	 * 增加 and 查询条件，大于等于查询，>=
	 * @param filterName
	 * @param value
	 */
	public void greaterEqual(String filterName, Object value) {
		this.andFilter(filterName, FilterEnum.GREATER_EQUAL, value);
	}

	/**
	 * 增加 and 查询条件，大于等于查询，>=，针对时间格式化查询条件
	 * @param filterName
	 * @param value
	 */
	public void greaterEqualDateFormat(String filterName, Object value, String pattern) {
		this.andFilter(filterName, FilterEnum.GREATER_EQUAL_DATE_FORMAT, value);
	}

	/**
	 * 增加 and 字段比较查询条件，大于等于查询，>=，例如 a >= b，a和b均为数据库字段
	 * @param filterName
	 * @param fieldName
	 */
	public void greaterEqualField(String filterName, String fieldName) {
		this.andFilter(filterName, FilterEnum.GREATER_EQUAL_FIELD, fieldName);
	}

	/**
	 * 增加连接查询 and 查询条件，大于等于查询，>=
	 * @param filterName
	 * @param value
	 */
	public void onGreaterEqual(String filterName, Object value) {
		this.andOnFilter(filterName, FilterEnum.GREATER_EQUAL, value);
	}

	/**
	 * 增加连接查询 and 查询条件，大于等于查询，>=，针对时间格式化查询条件
	 * @param filterName
	 * @param value
	 */
	public void onGreaterEqualDateFormat(String filterName, Object value, String pattern) {
		this.andOnFilter(filterName, FilterEnum.GREATER_EQUAL_DATE_FORMAT, value, pattern);
	}

	/**
	 * 增加连接查询 and 字段查询条件，大于等于查询，>=，例如 a >= b，a和b均为数据库字段
	 * @param filterName
	 * @param fieldName
	 */
	public void onGreaterEqualField(String filterName, String fieldName) {
		this.andOnFilter(filterName, FilterEnum.GREATER_EQUAL_FIELD, fieldName);
	}

	/**
	 * 增加 or 查询条件，大于等于查询，>=
	 * @param filterName
	 * @param value
	 */
	public void orGreaterEqual(String filterName, Object value) {
		this.or(filterName, FilterEnum.GREATER_EQUAL, value);
	}

	/**
	 * 增加 or 查询条件，大于等于查询，>=,针对时间格式化查询条件
	 * @param filterName
	 * @param value
	 */
	public void orGreaterEqualDateFormat(String filterName, Object value, String pattern) {
		this.or(filterName, FilterEnum.GREATER_EQUAL_DATE_FORMAT, value, pattern);
	}

	/**
	 * 增加 or 字段比较查询条件，大于等于查询，>=，例如 a >= b，a和b均为数据库字段
	 * @param filterName
	 * @param fieldName
	 */
	public void orGreaterEqualField(String filterName, String fieldName) {
		this.or(filterName, FilterEnum.GREATER_EQUAL_FIELD, fieldName);
	}

	/**
	 * 增加连接查询 or 查询条件，大于等于查询，>=
	 * @param filterName
	 * @param value
	 */
	public void onOrGreaterEqual(String filterName, Object value) {
		this.onOr(filterName, FilterEnum.GREATER_EQUAL, value);
	}

	/**
	 * 增加连接查询 or 查询条件，大于等于查询，>=，针对时间格式化查询条件
	 * @param filterName
	 * @param value
	 */
	public void onOrGreaterEqualDateFormat(String filterName, Object value, String pattern) {
		this.onOr(filterName, FilterEnum.GREATER_EQUAL_DATE_FORMAT, value, pattern);
	}

	/**
	 * 增加连接查询 or 字段查询条件，大于等于查询，>=，例如 a >= b，a和b均为数据库字段
	 * @param filterName
	 * @param fieldName
	 */
	public void onOrGreaterEqualField(String filterName, String fieldName) {
		this.onOr(filterName, FilterEnum.GREATER_EQUAL_FIELD, fieldName);
	}

	/**
	 * 增加 and 大于等于0的条件表达式，传入字段名称即可
	 * @param filterName
	 */
	public void greaterEqualZero(String filterName) {
		this.andFilter(filterName, FilterEnum.GREATER_EQUAL, 0);
	}

	/**
	 * 增加 and 大于等于0的条件表达式，传入字段名称即可,针对时间格式化查询条件
	 * @param filterName
	 */
	public void greaterEqualZeroDateFormat(String filterName, String pattern) {
		this.andFilter(filterName, FilterEnum.GREATER_EQUAL_DATE_FORMAT, 0, pattern);
	}

	/**
	 * 增加连接查询 and 大于等于0的条件表达式，传入字段名称即可
	 * @param filterName
	 */
	public void onGreaterEqualZero(String filterName) {
		this.andOnFilter(filterName, FilterEnum.GREATER_EQUAL, 0);
	}

	public void onGreaterEqualZeroDateFormat(String filterName, String pattern) {
		this.andOnFilter(filterName, FilterEnum.GREATER_EQUAL_DATE_FORMAT, 0, pattern);
	}

	/**
	 * 增加 or 大于等于0的条件表达式，传入字段名称即可
	 * @param filterName
	 */
	public void orGreaterEqualZero(String filterName) {
		this.or(filterName, FilterEnum.GREATER_EQUAL, 0);
	}

	/**
	 * 增加 or 大于等于0的条件表达式, 针对时间格式化查询条件
	 * @param filterName
	 */
	public void orGreaterEqualZeroDateFormat(String filterName, String pattern) {
		this.or(filterName, FilterEnum.GREATER_EQUAL_DATE_FORMAT, 0, pattern);
	}

	/**
	 * 增加连接查询 or 大于等于0的条件表达式，传入字段名称即可
	 * @param filterName
	 */
	public void onOrGreaterEqualZero(String filterName) {
		this.onOr(filterName, FilterEnum.GREATER_EQUAL, 0);
	}

	/**
	 * 增加连接查询 or 大于等于0的条件表达式，传入字段名称即可,针对时间格式化查询条件
	 * @param filterName
	 */
	public void onOrGreaterEqualZeroDateFormat(String filterName, String pattern) {
		this.onOr(filterName, FilterEnum.GREATER_EQUAL_DATE_FORMAT, 0, pattern);
	}

	/**
	 * 增加 and 查询条件，小于查询，<
	 * @param filterName
	 * @param value
	 */
	public void lessThan(String filterName, Object value) {
		this.andFilter(filterName, FilterEnum.LESS_THAN, value);
	}

	/**
	 * 增加 and 查询条件，小于查询，<,针对时间格式化查询条件
	 * @param filterName
	 * @param value
	 * @param pattern
	 */
	public void lessThanDateFormat(String filterName, Object value, String pattern) {
		this.andFilter(filterName, FilterEnum.LESS_THAN_DATE_FORMAT, value, pattern);
	}

	/**
	 * 增加 and 字段比较查询条件，小于查询，<，例如 a < b，a和b均为数据库字段
	 * @param filterName
	 * @param fieldName
	 */
	public void lessThanField(String filterName, String fieldName) {
		this.andFilter(filterName, FilterEnum.LESS_THAN_FIELD, fieldName);
	}

	/**
	 * 增加连接查询 and 查询条件，小于查询，<
	 * @param filterName
	 * @param value
	 */
	public void onLessThan(String filterName, Object value) {
		this.andOnFilter(filterName, FilterEnum.LESS_THAN, value);
	}

	/**
	 * 增加连接查询 and 查询条件，小于查询，<，针对时间格式化查询条件
	 * @param filterName
	 * @param value
	 */
	public void onLessThanDateFormat(String filterName, Object value, String pattern) {
		this.andOnFilter(filterName, FilterEnum.LESS_THAN_DATE_FORMAT, value, pattern);
	}

	/**
	 * 增加连接查询 and 字段比较查询条件，小于查询，<，例如 a < b，a和b均为数据库字段
	 * @param filterName
	 * @param fieldName
	 */
	public void onLessThanField(String filterName, String fieldName) {
		this.andOnFilter(filterName, FilterEnum.LESS_THAN_FIELD, fieldName);
	}

	/**
	 * 增加 or 查询条件，小于查询，<
	 * @param filterName
	 * @param value
	 */
	public void orLessThan(String filterName, Object value) {
		this.or(filterName, FilterEnum.LESS_THAN, value);
	}

	/**
	 * 增加 or 查询条件，小于查询，<，针对时间格式化查询条件
	 * @param filterName
	 * @param value
	 */
	public void orLessThanDateFormat(String filterName, Object value, String pattern) {
		this.or(filterName, FilterEnum.LESS_THAN_DATE_FORMAT, value, pattern);
	}

	/**
	 * 增加 or 字段比较查询条件，小于查询，<，例如 a < b，a和b均为数据库字段
	 * @param filterName
	 * @param fieldName
	 */
	public void orLessThanField(String filterName, String fieldName) {
		this.or(filterName, FilterEnum.LESS_THAN_FIELD, fieldName);
	}

	/**
	 * 增加连接查询 or 查询条件，小于查询，<
	 * @param filterName
	 * @param value
	 */
	public void onOrLessThan(String filterName, Object value) {
		this.onOr(filterName, FilterEnum.LESS_THAN, value);
	}

	/**
	 * 增加连接查询 or 查询条件，小于查询 <，针对时间格式化查询条件
	 * @param filterName
	 * @param value
	 */
	public void onOrLessThanDateFormat(String filterName, Object value, String pattern) {
		this.onOr(filterName, FilterEnum.LESS_THAN_DATE_FORMAT, value, pattern);
	}

	/**
	 * 增加连接查询 or 字段比较查询条件，小于查询，<，例如 a < b，a和b均为数据库字段
	 * @param filterName
	 * @param fieldName
	 */
	public void onOrLessThanField(String filterName, String fieldName) {
		this.onOr(filterName, FilterEnum.LESS_THAN_FIELD, fieldName);
	}

	/**
	 * 增加 and 查询条件，小于等于查询，<=
	 * @param filterName
	 * @param value
	 */
	public void lessEqual(String filterName, Object value) {
		this.andFilter(filterName, FilterEnum.LESS_EQUAL, value);
	}

	/**
	 * 增加 and 查询条件，小于等于查询，<=，针对时间格式化查询条件
	 * @param filterName
	 * @param value
	 */
	public void lessEqualDateFormat(String filterName, Object value, String pattern) {
		this.andFilter(filterName, FilterEnum.LESS_EQUAL_DATE_FORMAT, value, pattern);
	}

	/**
	 * 增加 and 字段比较查询条件，小于等于查询，<=，例如 a <= b，a和b均为数据库字段
	 * @param filterName
	 * @param fieldName
	 */
	public void lessEqualField(String filterName, String fieldName) {
		this.andFilter(filterName, FilterEnum.LESS_EQUAL_FIELD, fieldName);
	}

	/**
	 * 增加连接查询 and 查询条件，小于等于查询，<=
	 * @param filterName
	 * @param value
	 */
	public void onLessEqual(String filterName, Object value) {
		this.andOnFilter(filterName, FilterEnum.LESS_EQUAL, value);
	}

	/**
	 * 增加连接查询 and 查询条件，小于等于查询，<= 针对时间格式化查询条件
	 * @param filterName
	 * @param value
	 * @param pattern
	 */
	public void onLessEqualDateFormat(String filterName, Object value, String pattern) {
		this.andOnFilter(filterName, FilterEnum.LESS_EQUAL_DATE_FORMAT, value, pattern);
	}

	/**
	 * 增加连接查询 and 字段比较查询条件，小于等于查询，<=，例如 a <= b，a和b均为数据库字段
	 * @param filterName
	 * @param fieldName
	 */
	public void onLessEqualField(String filterName, String fieldName) {
		this.andOnFilter(filterName, FilterEnum.LESS_EQUAL_FIELD, fieldName);
	}

	/**
	 * 增加 or 查询条件，小于等于查询，<=
	 * @param filterName
	 * @param value
	 */
	public void orLessEqual(String filterName, Object value) {
		this.or(filterName, FilterEnum.LESS_EQUAL, value);
	}

	/**
	 * 增加 or 查询条件，小于等于查询，<= 针对时间格式化查询条件
	 * @param filterName
	 * @param value
	 * @param pattern
	 */
	public void orLessEqualDateFormat(String filterName, Object value, String pattern) {
		this.or(filterName, FilterEnum.LESS_EQUAL_DATE_FORMAT, value, pattern);
	}

	/**
	 * 增加 or 字段比较查询条件，小于等于查询，<=，例如 a <= b，a和b均为数据库字段
	 * @param filterName
	 * @param fieldName
	 */
	public void orLessEqualField(String filterName, String fieldName) {
		this.or(filterName, FilterEnum.LESS_EQUAL_FIELD, fieldName);
	}

	/**
	 * 增加连接查询 or 查询条件，小于等于查询，<=
	 * @param filterName
	 * @param value
	 */
	public void onOrLessEqual(String filterName, Object value) {
		this.onOr(filterName, FilterEnum.LESS_EQUAL, value);
	}

	/**
	 * 增加连接查询 or 查询条件，小于等于查询，<=  针对时间格式化查询条件
	 * @param filterName
	 * @param value
	 * @param pattern
	 */
	public void onOrLessEqualDateFormat(String filterName, Object value, String pattern) {
		this.onOr(filterName, FilterEnum.LESS_EQUAL_DATE_FORMAT, value, pattern);
	}

	/**
	 * 增加连接查询 or 字段比较查询条件，小于等于查询，<=，例如 a <= b，a和b均为数据库字段
	 * @param filterName
	 * @param fieldName
	 */
	public void onOrLessEqualField(String filterName, String fieldName) {
		this.onOr(filterName, FilterEnum.LESS_EQUAL_FIELD, fieldName);
	}

	/**
	 * 增加 and 查询，不等于查询，<>
	 * @param filterName
	 * @param value
	 */
	public void notEqual(String filterName, Object value) {
		this.andFilter(filterName, FilterEnum.NOT_EQUAL, value);
	}

	/**
	 * 增加 and 查询，不等于查询，<>  针对时间格式化查询条件
	 * @param filterName
	 * @param value
	 * @param pattern
	 */
	public void notEqualDateFormat(String filterName, Object value, String pattern) {
		this.andFilter(filterName, FilterEnum.NOT_EQUAL_DATE_FORMAT, value, pattern);
	}

	/**
	 * 增加 and 字段比较查询，不等于查询，<>，例如 a <> b，a和b均为数据库字段
	 * @param filterName
	 * @param fieldName
	 */
	public void notEqualField(String filterName, String fieldName) {
		this.andFilter(filterName, FilterEnum.NOT_EQUAL_FIELD, fieldName);
	}

	/**
	 * 增加连接查询 and 查询，不等于查询，<>
	 * @param filterName
	 * @param value
	 */
	public void onNotEqual(String filterName, Object value) {
		this.andOnFilter(filterName, FilterEnum.NOT_EQUAL, value);
	}

	/**
	 * 增加连接查询 and 查询，不等于查询，<>  针对时间格式化查询条件
	 * @param filterName
	 * @param value
	 * @param pattern
	 */
	public void onNotEqualDateFormat(String filterName, Object value, String pattern) {
		this.andOnFilter(filterName, FilterEnum.NOT_EQUAL_DATE_FORMAT, value, pattern);
	}

	/**
	 * 增加连接查询 and 查询，不等于查询，<>,例如 a <> b，a和b均为数据库字段
	 * @param filterName
	 * @param fieldName
	 */
	public void onNotEqualField(String filterName, String fieldName) {
		this.andOnFilter(filterName, FilterEnum.NOT_EQUAL_FIELD, fieldName);
	}

	/**
	 * 增加 or 查询，不等于查询，<>
	 * @param filterName
	 * @param value
	 */
	public void orNotEqual(String filterName, Object value) {
		this.or(filterName, FilterEnum.NOT_EQUAL, value);
	}

	/**
	 * 增加 or 查询，不等于查询，<> 针对时间格式化查询条件
	 * @param filterName
	 * @param value
	 * @param pattern
	 */
	public void orNotEqualDateFormat(String filterName, Object value, String pattern) {
		this.or(filterName, FilterEnum.NOT_EQUAL_DATE_FORMAT, value, pattern);
	}

	/**
	 * 增加 or 字段比较查询，不等于查询，<>,例如 a <> b，a和b均为数据库字段
	 * @param filterName
	 * @param fieldName
	 */
	public void orNotEqualField(String filterName, String fieldName) {
		this.or(filterName, FilterEnum.NOT_EQUAL_FIELD, fieldName);
	}

	/**
	 * 增加连接查询 or 查询，不等于查询，<>
	 * @param filterName
	 * @param value
	 */
	public void onOrNotEqual(String filterName, Object value) {
		this.onOr(filterName, FilterEnum.NOT_EQUAL, value);
	}

	/**
	 * 增加连接查询 or 查询，不等于查询，<>  针对时间格式化查询条件
	 * @param filterName
	 * @param value
	 * @param pattern
	 */
	public void onOrNotEqualDateFormat(String filterName, Object value, String pattern) {
		this.onOr(filterName, FilterEnum.NOT_EQUAL_DATE_FORMAT, value, pattern);
	}

	/**
	 * 增加连接查询 or 字段比较查询，不等于查询，<>,例如 a <> b，a和b均为数据库字段
	 * @param filterName
	 * @param fieldName
	 */
	public void onOrNotEqualField(String filterName, String fieldName) {
		this.onOr(filterName, FilterEnum.NOT_EQUAL_FIELD, fieldName);
	}

	/**
	 * 增加 and 查询条件，属于查询，in
	 * @param filterName
	 * @param value
	 */
	public void in(String filterName, Object value) {
		this.andFilter(filterName, FilterEnum.IN, value);
	}

	/**
	 * 增加 and 查询条件，属于查询，in >> 接收可变参数
	 * @param filterName
	 * @param value
	 */
	public void in(String filterName, Object...value) {
		this.andFilter(filterName, FilterEnum.IN, value);
	}

	/**
	 * 增加连接查询 and 查询条件，属于查询，in
	 * @param filterName
	 * @param value
	 */
	public void onIn(String filterName, Object value) {
		this.andOnFilter(filterName, FilterEnum.IN, value);
	}

	/**
	 * 增加连接查询 and 查询条件，属于查询，in >> 接收可变参数
	 * @param filterName
	 * @param value
	 */
	public void onIn(String filterName, Object...value) {
		this.andOnFilter(filterName, FilterEnum.IN, value);
	}

	/**
	 * 增加 or 查询条件，属于查询，in
	 * @param filterName
	 * @param value
	 */
	public void orIn(String filterName, Object value) {
		this.or(filterName, FilterEnum.IN, value);
	}

	/**
	 * 增加 or 查询条件，属于查询，in >> 接收可变参数
	 * @param filterName
	 * @param value
	 */
	public void orIn(String filterName, Object...value) {
		this.or(filterName, FilterEnum.IN, value);
	}

	/**
	 * 增加连接查询 or 查询条件，属于查询，in
	 * @param filterName
	 * @param value
	 */
	public void onOrIn(String filterName, Object value) {
		this.onOr(filterName, FilterEnum.IN, value);
	}

	/**
	 * 增加连接查询 or 查询条件，属于查询，in >> 接收可变参数
	 * @param filterName
	 * @param value
	 */
	public void onOrIn(String filterName, Object...value) {
		this.onOr(filterName, FilterEnum.IN, value);
	}

	/**
	 * 增加 and 查询条件，不属于查询，not in
	 * @param filterName
	 * @param value
	 */
	public void notIn(String filterName, Object value) {
		this.andFilter(filterName, FilterEnum.NOT_IN, value);
	}

	/**
	 * 增加 and 查询条件，不属于查询，not in >> 接收可变参数
	 * @param filterName
	 * @param value
	 */
	public void notIn(String filterName, Object...value) {
		this.andFilter(filterName, FilterEnum.NOT_IN, value);
	}

	/**
	 * 增加连接查询 and 查询条件，不属于查询，not in
	 * @param filterName
	 * @param value
	 */
	public void onNotIn(String filterName, Object value) {
		this.andOnFilter(filterName, FilterEnum.NOT_IN, value);
	}

	/**
	 * 增加连接查询 and 查询条件，不属于查询，not in  >> 接收可变参数
	 * @param filterName
	 * @param value
	 */
	public void onNotIn(String filterName, Object...value) {
		this.andOnFilter(filterName, FilterEnum.NOT_IN, value);
	}

	/**
	 * 增加 or 查询条件，不属于查询，not in
	 * @param filterName
	 * @param value
	 */
	public void orNotIn(String filterName, Object value) {
		this.or(filterName, FilterEnum.NOT_IN, value);
	}

	/**
	 * 增加 or 查询条件，不属于查询，not in  >>  接收可变参数
	 * @param filterName
	 * @param value
	 */
	public void orNotIn(String filterName, Object...value) {
		this.or(filterName, FilterEnum.NOT_IN, value);
	}

	/**
	 * 增加连接查询 or 查询条件，不属于查询，not in
	 * @param filterName
	 * @param value
	 */
	public void onOrNotIn(String filterName, Object value) {
		this.onOr(filterName, FilterEnum.NOT_IN, value);
	}

	/**
	 * 增加连接查询 or 查询条件，不属于查询，not in  >>  接收可变参数
	 * @param filterName
	 * @param value
	 */
	public void onOrNotIn(String filterName, Object...value) {
		this.onOr(filterName, FilterEnum.NOT_IN, value);
	}

	/**
	 * 增加 and 查询条件，表示null值查询，is null
	 * @param filterName
	 */
	public void isNull(String filterName) {
		this.andFilter(filterName, FilterEnum.IS_NULL, null);
	}

	/**
	 * 增加连接查询 and 查询条件，表示null值查询，is null
	 * @param filterName
	 */
	public void onIsNull(String filterName) {
		this.andOnFilter(filterName, FilterEnum.IS_NULL, null);
	}

	/**
	 * 增加 or 查询条件，表示null值查询，is null
	 * @param filterName
	 */
	public void orIsNull(String filterName) {
		this.or(filterName, FilterEnum.IS_NULL, null);
	}

	/**
	 * 增加连接查询 or 查询条件，表示null值查询，is null
	 * @param filterName
	 */
	public void onOrIsNull(String filterName) {
		this.onOr(filterName, FilterEnum.IS_NULL, null);
	}

	/**
	 * 增加 and 查询条件，表示null值查询，is not null
	 * @param filterName
	 */
	public void isNotNull(String filterName) {
		this.andFilter(filterName, FilterEnum.IS_NOT_NULL, null);
	}

	/**
	 * 增加连接查询 and 查询条件，表示null值查询，is not null
	 * @param filterName
	 */
	public void onIsNotNull(String filterName) {
		this.andOnFilter(filterName, FilterEnum.IS_NOT_NULL, null);
	}

	/**
	 * 增加 or 查询条件，表示null值查询，is not null
	 * @param filterName
	 */
	public void orIsNotNull(String filterName) {
		this.or(filterName, FilterEnum.IS_NOT_NULL, null);
	}

	/**
	 * 增加连接查询 or 查询条件，表示null值查询，is not null
	 * @param filterName
	 */
	public void onOrIsNotNull(String filterName) {
		this.onOr(filterName, FilterEnum.IS_NOT_NULL, null);
	}

	/**
	 * 增加 and 设定值后大于条件判断，比如count + 10 > 0
	 * @param filterName
	 * @param value
	 */
	public void upGreaterThanZero(String filterName, Object value) {
		this.andFilter(filterName, FilterEnum.UP_GREATER_THAN, value);
	}

	/**
	 * 增加连接查询 and 设定值后大于条件判断，比如count + 10 > 0
	 * @param filterName
	 * @param value
	 */
	public void onUpGreaterThanZero(String filterName, Object value) {
		this.andOnFilter(filterName, FilterEnum.UP_GREATER_THAN, value);
	}

	/**
	 * 增加 or 设定值后大于条件判断，比如count + 10 > 0
	 * @param filterName
	 * @param value
	 */
	public void orUpGreaterThanZero(String filterName, Object value) {
		this.or(filterName, FilterEnum.UP_GREATER_THAN, value);
	}

	/**
	 * 增加连接查询 or 设定值后大于条件判断，比如count + 10 > 0
	 * @param filterName
	 * @param value
	 */
	public void onOrUpGreaterThanZero(String filterName, Object value) {
		this.onOr(filterName, FilterEnum.UP_GREATER_THAN, value);
	}

	/**
	 * 增加 and 设定值后大于等于条件判断，比如count + 10 >= 0
	 * @param filterName
	 * @param value
	 */
	public void upGreaterEqualZero(String filterName, Object value) {
		this.andFilter(filterName, FilterEnum.UP_GREATER_EQUAL, value);
	}

	/**
	 * 增加连接查询 and 设定值后大于等于条件判断，比如count + 10 >= 0
	 * @param filterName
	 * @param value
	 */
	public void onUpGreaterEqualZero(String filterName, Object value) {
		this.andOnFilter(filterName, FilterEnum.UP_GREATER_EQUAL, value);
	}

	/**
	 * 增加 or 设定值后大于等于条件判断，比如count + 10 >= 0
	 * @param filterName
	 * @param value
	 */
	public void orUpGreaterEqualZero(String filterName, Object value) {
		this.or(filterName, FilterEnum.UP_GREATER_EQUAL, value);
	}

	/**
	 * 增加连接查询 or 设定值后大于等于条件判断，比如count + 10 >= 0
	 * @param filterName
	 * @param value
	 */
	public void onOrUpGreaterEqualZero(String filterName, Object value) {
		this.onOr(filterName, FilterEnum.UP_GREATER_EQUAL, value);
	}

	/**
	 * 增加 and 设定值后大于条件判断，比如count + 10 > 0
	 * @param filterName
	 * @param value
	 */
	public void reduceGreaterThanZero(String filterName, Object value) {
		this.andFilter(filterName, FilterEnum.REDUCE_GREATER_THAN, value);
	}

	/**
	 * 增加连接查询 and 设定值后大于条件判断，比如count + 10 > 0
	 * @param filterName
	 * @param value
	 */
	public void onReduceGreaterThanZero(String filterName, Object value) {
		this.andOnFilter(filterName, FilterEnum.REDUCE_GREATER_THAN, value);
	}

	/**
	 * 增加 or 设定值后大于条件判断，比如count + 10 > 0
	 * @param filterName
	 * @param value
	 */
	public void orReduceGreaterThanZero(String filterName, Object value) {
		this.or(filterName, FilterEnum.REDUCE_GREATER_THAN, value);
	}

	/**
	 * 增加连接查询 or 设定值后大于条件判断，比如count + 10 > 0
	 * @param filterName
	 * @param value
	 */
	public void onOrReduceGreaterThanZero(String filterName, Object value) {
		this.onOr(filterName, FilterEnum.REDUCE_GREATER_THAN, value);
	}

	/**
	 * 减少 and 设定值后小于等于条件判断，比如count - 10 >= 0
	 * @param filterName
	 * @param value
	 */
	public void reduceGreaterEqualZero(String filterName, Object value) {
		this.andFilter(filterName, FilterEnum.REDUCE_GREATER_EQUAL, value);
	}

	/**
	 * 连接查询 减少 and 设定值后小于等于条件判断，比如count - 10 >= 0
	 * @param filterName
	 * @param value
	 */
	public void onReduceGreaterEqualZero(String filterName, Object value) {
		this.andOnFilter(filterName, FilterEnum.REDUCE_GREATER_EQUAL, value);
	}

	/**
	 * 减少 or 设定值后小于等于条件判断，比如count - 10 >= 0
	 * @param filterName
	 * @param value
	 */
	public void orReduceGreaterEqualZero(String filterName, Object value) {
		this.or(filterName, FilterEnum.REDUCE_GREATER_EQUAL, value);
	}

	/**
	 * 连接查询 减少 or 设定值后小于等于条件判断，比如count - 10 >= 0
	 * @param filterName
	 * @param value
	 */
	public void onOrReduceGreaterEqualZero(String filterName, Object value) {
		this.onOr(filterName, FilterEnum.REDUCE_GREATER_EQUAL, value);
	}

	/**
	 * 添加 or 查询条件，比如 and (type = 1 or name = 2)，主要作用于拼接 and 后括号中的表达式，主要用于 or
	 * 查询的表达式，不然没必要。 如果 多条件拼接 or 查询(类似 where id = ? or type = 1
	 * 的条件)，or 条件查询不能被当成第一个条件放入(type属性 orFilter 方法不能在第一个加入)，否则会被解析为 and 条件查询。
	 * @param queryProvider
	 */
	public void orProvider(QueryProvider queryProvider) {
		if (queryProvider == null) {
			throw new HandleException("error: queryProvider is null");
		}

		if (this.orProviders == null) {
			orProviders = new ArrayList<>();
		}

		this.orProviders.add(queryProvider);
	}

	/**
	 * 添加 left join 查询，会被拼接到left join 的连体SQL。 当使用这个属性时，必须设置 joinTableName的连接表名。
	 * @param fieldName        表示left join 前面一张关联字段。
	 * @param paramFieldName   表示left join 后紧跟表的关联字段。
	 * @param queryProvider    被left join的封装对象。
	 */
	public void setleftJoin(String fieldName, String paramFieldName, QueryProvider queryProvider) {
		if (fieldName == null) {
			throw new HandleException("error: left join fieldName is null");
		}
		if (paramFieldName == null) {
			throw new HandleException("error: left join paramFieldName is null");
		}
		if (queryProvider == null) {
			throw new HandleException("error: queryProvider can't null");
		}
		if (ValidateTool.isEmpty(queryProvider.getJoinTableName())) {
			throw new HandleException("error: queryProvider joinTableName is null");
		}

		if (this.leftJoinProviders == null) {
			leftJoinProviders = new ArrayList<>();
		}

		Object[] obj = { fieldName, paramFieldName, queryProvider };
		this.leftJoinProviders.add(obj);
	}

	/**
	 * 添加 left join 查询，会被拼接到left join 的连体SQL。 当使用这个属性时，必须设置 joinTableName
	 * 的连接表名。 针对多条件，两数组长度必须一致。
	 * @param fieldName         表示left join 前面一张关联字段。
	 * @param paramFieldName    表示left join 后紧跟表的关联字段。
	 * @param queryProvider             被left join的封装对象。
	 */
	public void setleftJoin(String[] fieldName, String[] paramFieldName, QueryProvider queryProvider) {
		int fieldLength = 0;
		if (fieldName == null || (fieldLength = fieldName.length) == 0) {
			throw new HandleException("error: left join fieldName is null");
		}
		int paramFieldLength = 0;
		if (paramFieldName == null || (paramFieldLength = paramFieldName.length) == 0) {
			throw new HandleException("error: left join paramFieldName is null");
		}
		if (fieldLength != paramFieldLength) {
			throw new HandleException("error: left join 'on' filter length must be equal");
		}
		if (queryProvider == null) {
			throw new HandleException("error: queryProvider is null");
		}

		if (this.leftJoinProviders == null) {
			leftJoinProviders = new ArrayList<>();
		}

		Object[] obj = { fieldName, paramFieldName, queryProvider };
		this.leftJoinProviders.add(obj);
	}

	/**
	 * 增加排序，参数分别为排序字段，排序值，排序值类型参考 QueryParam 中 ORDER 开头的常量
	 * @param orderName
	 * @param orderType
	 */
	public void setOrder(String orderName, OrderEnum orderType) {
		this.setOrder(orderName, orderType, SqlHandleEnum.HANDLE_DEFAULT);
	}

	/**
	 * 针对 sum 聚合函数的排序
	 * @param orderName
	 * @param orderType
	 */
	public void setSumOrder(String orderName, OrderEnum orderType) {
		this.setOrder(orderName, orderType, SqlHandleEnum.HANDLE_SUM);
	}

	/**
	 * 针对平均数 avg 聚合函数的排序
	 * @param orderName
	 * @param orderType
	 */
	public void setAvgOrder(String orderName, OrderEnum orderType) {
		this.setOrder(orderName, orderType, SqlHandleEnum.HANDLE_AVG);
	}

	/**
	 * 支持传入表达式的排序
	 * @param orderName
	 * @param orderType
	 */
	public void setExpOrder(String orderName, OrderEnum orderType) {
		this.setOrder(orderName, orderType, SqlHandleEnum.HANDLE_EXP);
	}

	/**
	 * 增加排序，参数分别为排序字段，排序值，排序值类型参考 QueryParam 中 ORDER 开头的常量
	 * @param orderName
	 * @param orderType
	 */
	private void setOrder(String orderName, OrderEnum orderType, SqlHandleEnum sqlHandleEnum) {
		if (ValidateTool.isEmpty(orderName) && !sqlHandleEnum.equals(SqlHandleEnum.HANDLE_COUNT)) {
			throw new HandleException("error: order field is null ！！！");
		}

		if (this.orders == null) {
			this.orders = new ArrayList<>();
		}
		abstractOrder.addOrder(orders, orderName, orderType, sqlHandleEnum);
	}


	/**
	 * 增加分组，根据字段名称进行分组
	 * @param groupName
	 */
	public void setGroup(String groupName) {
		this.setGroups(groupName, SqlHandleEnum.HANDLE_DEFAULT, null);
	}

	public void setGroupDateFormat(String groupName, String pattern) {
		if (ValidateTool.isEmpty(pattern)) {
			throw new HandleException("error: pattern is null");
		}
		this.setGroups(groupName, SqlHandleEnum.HANDLE_DATE_FORMAT, pattern);
	}

	private void setGroups(String groupName, SqlHandleEnum sqlHandleEnum, String pattern) {
		if (ValidateTool.isEmpty(groupName)) {
			throw new HandleException("error: group field is null");
		}

        /**
         *  判断传入的分组字段是否包含特殊字符
         */
        String regExp = "[~!/@#$%^&*()=+\\|[{}];:\'\",<.>/?]+";
        Pattern p = Pattern.compile(regExp);
        Matcher m = p.matcher(groupName);
        if(m.find()) {
            throw new HandleException("error: group field is invalid");
        }

		if (this.groups == null) {
			this.groups = new ArrayList<>();
		}
		Object[] group = {groupName, sqlHandleEnum, pattern};
		this.groups.add(group);
	}

	/**
	 * 移除所有查询条件
	 * @param
	 */
	public void removeFilter() {
		this.filters.clear();
	}

	/**
	 * 根据前端传入的 command 实体，获取查询属性的 @QueryFilter 注解值
	 * obj 必须为 RequestParam 的子类，否则抛出 HandleException 异常
	 * @author HuangLongPu
	 * @param obj
	 */
	public void setFilters(Object obj) {
		if (!(obj instanceof RequestParam)) {
			throw new HandleException("error: the filter is not instanceof RequestParam");
		}
		if(updateObj != null && updateObj == obj) {
			return;
		}
		QueryHandle.getFilters(obj, this);
	}

	/**
	 * 根据前端传入的 command 实体，获取修改属性的 @UpdateField 注解值
	 * obj 必须为 RequestParam 的子类，否则抛出 HandleException 异常
	 * @param obj
	 */
	public void setUpdate(Object obj) {
		if (!(obj instanceof RequestParam)) {
			throw new HandleException("error: the update is not instanceof RequestParam");
		}
		updateObj = obj;
		QueryHandle.getUpdateField(obj, this);
		QueryHandle.getFilters(obj, this);
	}
	
	/**
	 * 传入 ResultInfoOutput 的子类进行自动转换。
	 * 如果接收的属性与数据库字段不一致，用@Column 注解映射，映射可以是实体属性名和字段名。
	 * 如果有属性不想被添加到addField中，用@NotColumn 注解映射，将会自动过滤。
	 * @param cls
	 */
	public void setColumn(Class<?> cls) {
		if(!ResultInfoOutput.class.isAssignableFrom(cls)) {
			throw new HandleException("error: the select is not instanceof ResultInfoOutput");
		}
		
		List<String[]> result = BeanCacheConvert.getResultFields(cls);
		for (String[] field : result) {
			this.set(field[0], field[1]);
		}
	}

}
