package orm;

import orm.GroupBy.GroupByProperty;
import orm.Sort.Direction;
import orm.Sort.Order;
import orm.WhereClause.QueryParam;
import orm.ReflectionUtil;
import orm.CollectionUtil;
import orm.RegexUtil;
import orm.StringUtil;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.persistence.EntityManager;

import org.apache.commons.lang3.ArrayUtils;
import org.hibernate.Session;
import org.jboss.seam.Component;
import org.jboss.seam.annotations.Transactional;
import org.jboss.seam.core.Expressions;
import org.jboss.seam.core.Expressions.ValueExpression;
import org.jboss.seam.framework.Controller;
import org.jboss.seam.web.Parameters;

/**
 * @author omid <br />
 *         <p>
 *         this is a copy of Seam EntityQuery for native queries
 *         </p>
 *         <p>
 *         <blockquote>
 * 
 *         <pre>
 * QueryController qc = new QueryController();
 * qc.setQuery();
 * qc.addWhereClause();
 * qc.execute();
 * </pre>
 * 
 *         </blockquote>
 *         <p>
 *         <br />
 *         steps to use this controller <br />
 *         1. set query (also available in constructor)<br />
 *         2. add where clause (OPTIONAL) <br />
 *         3. add order by (Optional) <br />
 *         4. add group by (Optional) <br />
 *         5. call createQuery method <br />
 *         6. call execute method <br />
 */
public class QueryController extends Controller
{

    private static final Logger logger = Logger.getLogger(QueryController.class.getName());
    private final static Pattern pattern = Pattern.compile("from(.+?)(LEFT|RIGHT|INNER)", Pattern.CASE_INSENSITIVE);
    private final static Pattern FROM_PATTERN = Pattern.compile("(from)\\s(.*)", Pattern.CASE_INSENSITIVE);
    private static final String SPACE = " ";

    private String query;

    private StringBuffer queryBuffer;

    private StringBuffer countQueryBuffer;

    private List<WhereClause> whereClauseList;

    private List<String> queryAppenders;

    private Sort sort;

    private GroupBy groupBy;

    private Root root;

    private Integer firstResult;

    private Integer maxResults;

    public Integer pageNumber;

    private List resultList;

    private Long resultCount;

    /**
     * values inject into query directly
     */
    private Object[] queryParamValues;

    /**
     * all sql values inject into queries
     */
    private List<Object> values = new ArrayList<Object>();

    public void setQueryParamValues(Object... queryParamValues)
    {
        this.queryParamValues = queryParamValues;
    }

    protected EntityManager getEntityManager()
    {
        return (EntityManager) Component.getInstance("entityManager");
    }

    protected Session getSession()
    {
        return (Session) Component.getInstance("hibernateSession");
    }

    public void addWhereClause(WhereClause whereClause)
    {
        if (CollectionUtil.isEmpty(whereClauseList))
            whereClauseList = new ArrayList<WhereClause>();
        this.whereClauseList.add(whereClause);
    }

    public void setQuery(String query)
    {
        this.query = query;
        if (StringUtil.isEmpty(getQueryAlias()))
            this.root = new Root(query);
        else
            this.root = new Root(getQueryAlias());
    }

    /**
     * this method is used for appending dynamic query in addRestriction method
     * 
     * @param joinQuery
     */
    public void appendQuery(String joinQuery)
    {
        if (this.query == null)
        {
            throw new IllegalArgumentException("You have to set query first");
        }
        if (queryAppenders == null)
            queryAppenders = new ArrayList<String>();
        queryAppenders.add(joinQuery);
    }

    public Query createQuery()
    {
        if (this.query == null)
        {
            throw new IllegalArgumentException("You have to provide query");
        }
        //
        queryBuffer = new StringBuffer(this.query.replaceAll("[\\r|\\t|\\n]", " "));
        // where clause is not null means it specify outside of restrictions
        // method so don't add restrictions
        if (whereClauseList == null)
        {
            whereClauseList = new ArrayList<WhereClause>();
            addRestrictions();
            addToExpressions();
        }

        applyAppenders(queryBuffer);

        applyWhere(queryBuffer);
        // Group
        if (groupBy != null)
        {
            applyGroupBy(queryBuffer);
        }
        // Sort
        if (sort != null)
        {
            applyOrderBy(queryBuffer);
        }
        //
        if (getMaxResults() != null)
            queryBuffer.append(" limit ").append(getMaxResults());

        if (getFirstResult() != null)
        {
            if (getFirstResult() > 0)
                queryBuffer.append(" offset ").append(getFirstResult());
        }
        return new Query(queryBuffer.toString(), values);
    }

    private void applyAppenders(StringBuffer qb)
    {
        if (CollectionUtil.isNotEmpty(queryAppenders))
        {
            for (String q : queryAppenders)
            {
                qb.append(q);
            }
        }
    }

    private void applyGroupBy(StringBuffer queryBuffer2)
    {
        if (RegexUtil.find(queryBuffer.toString(), "Group By") == false)
            queryBuffer.append(" Group By ");
        int i = 0;
        for (GroupByProperty prop : groupBy)
        {
            if (i > 0)
                queryBuffer.append(", ");
            if (prop.getPropertyName().contains("."))
                queryBuffer.append(prop.getPropertyName());
            else
                queryBuffer.append(this.root.getAlias()).append(".").append(prop.getPropertyName());
            i++;
        }
    }

    private void applyOrderBy(StringBuffer queryBuffer)
    {
        if (RegexUtil.find(queryBuffer.toString(), "Order By") == false)
            queryBuffer.append(" Order By ");
        int i = 0;
        for (Order order : sort)
        {
            if (i > 0)
                queryBuffer.append(", ");
            Direction direction = order.getDirection();
            if (direction.equals(Direction.QUERY))
            {
                queryBuffer.append(" ").append(order.getProperty());
            }
            else
            {
                if (order.getProperty().contains("."))
                    queryBuffer.append(order.getProperty()).append(" ").append(direction.getLabel());
                else
                    queryBuffer.append(this.root.getAlias()).append(".").append(order.getProperty()).append(" ")
                            .append(direction.getLabel());
            }
            i++;
        }
    }

    private void applyWhere(StringBuffer queryBuffer)
    {
        values = new ArrayList<Object>(); // must reset values
        int wcCount = 0;
        if (RegexUtil.find(queryBuffer.toString(), "where"))
        {
            if (CollectionUtil.isNotEmpty(whereClauseList))
                queryBuffer.append(" AND "); // maybe developer should
                                             // define
            // this operand
        }
        if (RegexUtil.find(queryBuffer.toString(), "where") == false)
        {
            if (CollectionUtil.isNotEmpty(whereClauseList))
                queryBuffer.append(" WHERE ");
        }
        for (WhereClause wc : whereClauseList)
        {
            parseWhereClause(wc, queryBuffer, wcCount);
            wcCount++;
        }
    }

    private void parseWhereClause(WhereClause wc, StringBuffer qb, int clauseCount)
    {
        StringBuilder sb = new StringBuilder();
        int queryCount = 0;
        boolean useOperandGrouping = false;
        LogicalOperand logicalOperand = wc.getLogicalOperand();
        for (QueryParam qp : wc)
        {
            Object value = qp.getValue(); // can be comma separated seam
                                          // expression
            Operator paramOperator = qp.getOperator();
            if ((Operator.IS_NULL.equals(paramOperator) || Operator.NOT_NULL.equals(paramOperator)) == false)
            {
                if (shouldIgnoreClause(value))
                    continue;
            }
            //
            useOperandGrouping = true;
            //
            String columnExpression = qp.getColumnExpression();
            if (columnExpression.contains(".") == false)
                columnExpression = root.getAlias() + "." + columnExpression;

            Function function = qp.getFunction();
            if (function != null)
                columnExpression = function.parseColumn(columnExpression);

            if (value != null && value instanceof Date)
            {
                // jdbc uses sql date (convert java util date to sql date
                Date d = (Date) value;
                java.sql.Date dt = new java.sql.Date(d.getTime());
                value = dt;
            }

            if (queryCount > 0)
                sb.append(SPACE).append(logicalOperand.name()).append(SPACE);
            queryCount++;

            if (Operator.QUERY.equals(paramOperator))
            {
                sb.append(columnExpression);
                parseValue(value, paramOperator, function);
            }
            else
            {
                sb.append(columnExpression);
                if (Operator.EQUAL.equals(paramOperator))
                    sb.append(" = ?");
                if (Operator.NOTEQUAL.equals(paramOperator))
                    sb.append(" <> ?");
                if (Operator.GT.equals(paramOperator))
                    sb.append(" > ?");
                if (Operator.GTE.equals(paramOperator))
                    sb.append(" >= ?");
                if (Operator.LT.equals(paramOperator))
                    sb.append(" < ?");
                if (Operator.LTE.equals(paramOperator))
                    sb.append(" <= ?");
                if (Operator.LIKE.equals(paramOperator) || Operator.BEGIN_WITH.equals(paramOperator)
                        || Operator.END_WITH.equals(paramOperator))
                {
                    sb.append(" LIKE ? ");
                }
                if (Operator.IS_NULL.equals(paramOperator))
                {
                    sb.append(" is null ");
                    continue;
                }
                if (Operator.NOT_NULL.equals(paramOperator))
                {
                    sb.append(" is not null ");
                    continue;
                }

                if (Operator.IN.equals(paramOperator) || Operator.NOT_IN.equals(paramOperator))
                {
                    value = In(value);
                    if (Operator.IN.equals(paramOperator))
                        sb.append(" IN( ");
                    if (Operator.NOT_IN.equals(paramOperator))
                        sb.append(" NOT IN( ");
                    sb.append(value);
                    sb.append(" ) ");
                    continue;
                }

                parseValue(value, paramOperator, function);

            }

        }
        if (useOperandGrouping)
        {
            if (clauseCount > 0)
                qb.append(SPACE).append(wc.getLogicalOperandClause().name()).append(SPACE);
            GroupOperand groupOperand = wc.getGroupOperand();
            if (groupOperand.equals(GroupOperand.GROUP))
                qb.append(" ( ");
            qb.append(sb.toString());
            if (groupOperand.equals(GroupOperand.GROUP))
                qb.append(" ) ");
        }
    }

    private boolean shouldIgnoreClause(Object value)
    {
        if (value == null)
            return true;
        if (isSeamExpression(value))
        {
            value = Expressions.instance().createValueExpression((String) value).getValue();
            if (value == null)
                return true;
            if (value instanceof String)
            {
                if (StringUtil.isEmpty((String) value))
                    return true;
            }
        }
        return false;
    }

    /**
     * @param value
     *            comma separated String/Int or List of String/Int
     * @return
     */
    private String In(Object value)
    {
        StringBuilder sb = new StringBuilder();
        if (value.getClass().isArray())
        {
            Object[] inObject = (Object[]) value;
            for (int j = 0; j < inObject.length; j++)
            {
                Object objectVal = inObject[j];
                if (j > 0)
                    sb.append(", ");
                if (objectVal instanceof String)
                    sb.append("'").append(objectVal).append("'");
                else
                    sb.append(objectVal);
            }
        }
        if (ReflectionUtil.isSubclass(value.getClass(), Collection.class))
        {
            List<?> inObject = (List<?>) value;
            for (int j = 0; j < inObject.size(); j++)
            {
                Object objectVal = inObject.get(j);
                if (j > 0)
                    sb.append(", ");
                if (objectVal instanceof String)
                    sb.append("'").append(objectVal).append("'");
                else
                    sb.append(objectVal);
            }
        }
        if (value instanceof String)
        {
            String inValue = (String) value;
            if (isSeamExpression(inValue))
            {
                throw new UnsupportedOperationException("in values can not be Seam Expression");
            }
            sb.append(inValue);
        }

        return sb.toString();
    }

    /**
     * used only for query operator
     * 
     * @param paramOperator
     * 
     * @param ejbql
     * @return
     */
    private void parseValue(Object val, Operator paramOperator, Function function)
    {

        if (val instanceof String)
        {
            String valueExpression = (String) val;
            if (valueExpression.contains(","))
            {
                // more than one parameter value
                String[] valueItem = valueExpression.split(",");
                for (String item : valueItem)
                {

                    parseExpression(item, paramOperator, function);
                }
            }
            else
            {
                parseExpression(valueExpression, paramOperator, function);
            }

        }
        else
        {
            // is not string
            if (val != null)
            {
                if (function != null)
                    val = function.parseColumnValue(val);
                values.add(val);
            }
        }

    }

    private void parseExpression(String valueExpression, Operator paramOperator, Function function)
    {
        // is seam expression
        Object evalValue = null;
        if (isSeamExpression(valueExpression))
        {
            ValueExpression<Object> expression = Expressions.instance().createValueExpression(valueExpression);
            Object value = expression.getValue();
            if (value != null)
            {
                evalValue = value;
            }
        }
        else
        {
            evalValue = valueExpression;
        }

        if (evalValue != null)
        {
            if (Operator.LIKE.equals(paramOperator))
            {
                evalValue = "%" + evalValue + "%";
            }
            if (Operator.BEGIN_WITH.equals(paramOperator))
            {
                evalValue = evalValue + "%";
            }
            if (Operator.END_WITH.equals(paramOperator))
            {
                evalValue = "%" + evalValue;
            }
            if (function != null)
                evalValue = function.parseColumnValue(evalValue);
            values.add(evalValue);
        }
    }

    private boolean isSeamExpression(Object valueExpression)
    {
        if (valueExpression instanceof String)
        {
            String ve = (String) valueExpression;
            return ve.startsWith("#{") && ve.endsWith("}");
        }
        return false;
    }

    protected Query createCountQuery()
    {
        if (StringUtil.isEmpty(this.query))
        {
            throw new IllegalArgumentException("query is not set");
        }
        if (StringUtil.isEmpty(getCountQueryProjection()))
            countQueryBuffer = new StringBuffer("select count(*) from ");
        else
            countQueryBuffer = new StringBuffer(String.format("select %s from ", getCountQueryProjection()));
        String cnt = this.query.replaceAll("[\\r|\\t|\\n]", " ");
        final Matcher matcher = FROM_PATTERN.matcher(cnt);
        if (matcher.find())
            countQueryBuffer.append(matcher.group(2));
        if (whereClauseList == null)
        {
            whereClauseList = new ArrayList<WhereClause>();
            addRestrictions();
            addToExpressions();
        }

        applyAppenders(countQueryBuffer);

        applyWhere(countQueryBuffer);
        if (groupBy != null)
        {
            applyGroupBy(countQueryBuffer);
        }
        return new Query(countQueryBuffer.toString(), values);
    }

    protected String getCountQueryProjection()
    {
        return null;
    }

    private static class Root
    {
        private String alias;

        public Root(String alias)
        {
            if (StringUtil.isEmpty(alias))
            {
                throw new IllegalArgumentException("query is not valid");
            }
            alias = alias.replaceAll("[\\r|\\t|\\n]", " ");
            final Matcher matcher = pattern.matcher(alias);
            String[] split = null;
            if (matcher.find())
                split = matcher.group(1).split("\\s");
            else
                split = alias.split("\\s");
            this.alias = split[split.length - 1];
            // logger.info(this.alias);
        }

        public String getAlias()
        {
            return alias;
        }

    }

    private Object[] bindParameters(Object[] params, List<Object> paramValues)
    {
        if (params != null && CollectionUtil.isNotEmpty(paramValues))
        {
            return ArrayUtils.addAll(params, paramValues.toArray(new Object[paramValues.size()]));
        }
        if (params != null)
        {
            return params;
        }
        if (CollectionUtil.isNotEmpty(paramValues))
            return paramValues.toArray(new Object[paramValues.size()]);
        return null;
    }

    /**
     * params take precedence over query where values
     * 
     * @param queryRunner
     * @param params
     * @return
     */
    @Transactional
    public List<Map<String, Object>> execute(Query queryRunner)
    {
        String queryToExecute = queryRunner.getQueryToExecute();
        List<Object> paramValues = queryRunner.getVals();
        boolean hasQueryParams = CollectionUtil.isNotEmpty(paramValues) || this.queryParamValues != null;

        if (hasQueryParams == false)
        {
            List<Map<String, Object>> list = DBUtil.instance().executeQuery(queryToExecute);
            resultList = list;
            return list;
        }
        //
        Object[] parameters = bindParameters(this.queryParamValues, paramValues);
        List<Map<String, Object>> list = DBUtil.instance().executeQuery(queryToExecute, parameters);

        resultList = list;
        return list;
    }

    @Transactional
    public <T> T executeForObject(Class<T> clz, Query queryRunner)
    {
        List<Object> paramValues = queryRunner.getVals();
        boolean hasQueryParams = CollectionUtil.isNotEmpty(paramValues) || this.queryParamValues != null;
        if (hasQueryParams == false)
        {
            return DBUtil.instance().executeQuery(clz, queryRunner.getQueryToExecute());
        }
        Object[] parameters = bindParameters(this.queryParamValues, paramValues);
        return DBUtil.instance().executeQuery(clz, queryRunner.getQueryToExecute(), parameters);
    }

    @Transactional
    public <E> List<E> executeForList(Class<E> clz, Query queryRunner)
    {
        List<Object> paramValues = queryRunner.getVals();
        boolean hasQueryParams = CollectionUtil.isNotEmpty(paramValues) || this.queryParamValues != null;
        if (hasQueryParams == false)
        {
            List<E> queryList = DBUtil.instance().executeQueryList(clz, queryRunner.getQueryToExecute());
            resultList = queryList;
            return queryList;
        }
        Object[] parameters = bindParameters(this.queryParamValues, paramValues);
        List<E> queryList = DBUtil.instance().executeQueryList(clz, queryRunner.getQueryToExecute(), parameters);
        resultList = queryList;
        return queryList;
    }

    @Deprecated
    public <E> List<E> executeNativeQuery(Class<E> clz, Query queryRunner)
    {

        javax.persistence.Query nq = getEntityManager().createNativeQuery(queryRunner.getQueryToExecute(), clz);
        List<Object> vals = queryRunner.getVals();
        if (vals != null && vals.size() > 0)
        {
            int start = 0;
            for (int i = 0; i < vals.size(); i++)
            {
                nq.setParameter(++start, vals.get(i));
            }
        }
        return nq.getResultList();
    }

    public void addOrderBy(Sort sort)
    {
        this.sort = sort;
    }

    public void addGroupBy(GroupBy groupBy)
    {
        this.groupBy = groupBy;
    }

    public static class Query implements Serializable
    {
        private String queryToExecute;
        private List<Object> vals;

        public Query(String queryToExecute, List<Object> vals)
        {
            this.queryToExecute = queryToExecute;
            this.vals = vals;
        }

        public String getQueryToExecute()
        {
            return queryToExecute;
        }

        public List<Object> getVals()
        {
            return vals;
        }

    }

    public enum LogicalOperand
    {
        AND, OR;
    }

    public enum GroupOperand
    {
        GROUP, NOGROUP;
    }

    public Integer getFirstResult()
    {
        if (pageNumber != null && getPageCount() != null)
        {
            return getPageNumber() * getMaxResults();
        }
        return firstResult;
    }

    @Transactional
    public Integer getPageCount()
    {
        if (getMaxResults() == null)
        {
            return null;
        }
        else
        {
            int rc = getResultCount().intValue();
            int mr = getMaxResults().intValue();
            int pages = rc / mr;
            return rc % mr == 0 ? pages : pages + 1;
        }
    }

    @Transactional
    public Long getResultCount()
    {
        Query countQuery = createCountQuery();
        if (isAnyParameterDirty())
        {
            refresh();
        }
        if (resultCount != null)
            return resultCount;
        List<Object> paramValues = countQuery.getVals();
        boolean hasParam = CollectionUtil.isNotEmpty(paramValues) || this.queryParamValues != null;
        Long count = null;
        if (hasParam)
        {
            Object[] parameters = bindParameters(this.queryParamValues, paramValues);
            count = DBUtil.instance().executeScalar(countQuery.getQueryToExecute(), Long.class, parameters);
        }
        else
            count = DBUtil.instance().executeScalar(countQuery.getQueryToExecute(), Long.class);
        resultCount = count == null ? null : count;
        addToExpressionValues();
        return resultCount;
    }

    public void setFirstResult(Integer firstResult)
    {
        this.firstResult = firstResult;
    }

    public Integer getPageNumber()
    {
        if (pageNumber == null || pageNumber < 0)
            pageNumber = 0;
        return pageNumber;
    }

    public void setPageNumber(Integer pageNumber)
    {
        this.pageNumber = pageNumber;
    }

    public Integer getMaxResults()
    {
        return maxResults;
    }

    public void setMaxResults(Integer maxResults)
    {
        this.maxResults = maxResults;
    }

    public boolean isPreviousExists()
    {
        return (getFirstResult() != null && getFirstResult() != 0) && CollectionUtil.isNotEmpty(resultList);
    }

    @Transactional
    public boolean isNextExists()
    {
        return resultList != null && getMaxResults() != null && resultList.size() >= getMaxResults();
    }

    public int getNextFirstResult()
    {
        Integer fr = getFirstResult();
        return (fr == null ? 0 : fr) + getMaxResults();
    }

    /**
     * Get the index of the first result of the previous page
     * 
     */
    public int getPreviousFirstResult()
    {
        Integer fr = getFirstResult();
        Integer mr = getMaxResults();
        return mr >= (fr == null ? 0 : fr) ? 0 : fr - mr;
    }

    @Transactional
    public Long getLastFirstResult()
    {
        Integer pc = getPageCount();
        return pc == null ? null : (pc.longValue() - 1) * getMaxResults();
    }

    protected LogicalOperand operandValueOf(String operandExpression)
    {
        String op = parseValueExpression(operandExpression, String.class);
        return StringUtil.isNotEmpty(op) ? LogicalOperand.valueOf(op) : LogicalOperand.AND;
    }

    protected <T> T resolveRequestParameter(String paramName, Class<T> type)
    {
        Parameters parameters = Parameters.instance();
        Object requestParameter = parameters.convertMultiValueRequestParameter(parameters.getRequestParameters(), paramName, type);
        if (requestParameter == null)
            return null;
        return (T) requestParameter;
    }

    protected void reset()
    {
        setMaxResults(null);
        setFirstResult(null);
        addGroupBy(null);
        addOrderBy(null);
        this.queryParamValues = null;
        pageNumber = 0;
    }

    protected void refresh()
    {
        resultCount = null;
        resultList = null;
    }

    protected <T> T parseValueExpression(String seamExpression, Class<T> type)
    {
        ValueExpression<T> exprVal = Expressions.instance().createValueExpression(seamExpression, type);
        if (exprVal.getValue() == null)
            return null;
        return exprVal.getValue();
    }

    private List<ValueExpression> valueExpressions;
    private List<Object> expressionValues;

    protected void addToExpressions()
    {
        if (CollectionUtil.isNotEmpty(whereClauseList))
        {
            valueExpressions = new ArrayList<Expressions.ValueExpression>();
            for (WhereClause wc : whereClauseList)
            {
                for (QueryParam queryParam : wc)
                {
                    Object ve = queryParam.getValue();
                    if (isSeamExpression(ve))
                        valueExpressions.add(Expressions.instance().createValueExpression((String) ve));
                }
            }
        }

    }

    protected void addToExpressionValues()
    {
        if (CollectionUtil.isNotEmpty(whereClauseList))
        {
            expressionValues = new ArrayList<Object>();
            for (WhereClause wc : whereClauseList)
            {
                for (QueryParam queryParam : wc)
                {
                    Object ve = queryParam.getValue();
                    if (isSeamExpression(ve))
                        expressionValues.add(Expressions.instance().createValueExpression((String) ve).getValue());
                }
            }
        }

    }

    protected boolean isAnyParameterDirty()
    {
        if (expressionValues == null || valueExpressions == null)
            return true;
        for (int i = 0; i < valueExpressions.size(); i++)
        {
            if (valueExpressions.size() == expressionValues.size())
            {
                Object expressionValue = valueExpressions.get(i).getValue();
                Object value = expressionValues.get(i);
                if (expressionValue != value && (expressionValue == null || !expressionValue.equals(value)))
                    return true;
            }
        }

        return false;
    }

    protected void addRestrictions()
    {
    }

    /**
     * define alias for complex query
     * 
     * @return
     */
    protected String getQueryAlias()
    {
        return null;
    }

}
