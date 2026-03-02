package org.vstu.meaningtree.languages.helpers;

import org.vstu.meaningtree.languages.helpers.query.CompiledTSQuery;
import org.vstu.meaningtree.languages.helpers.query.ParseSession;
import org.vstu.meaningtree.languages.helpers.query.QueryResult;

public interface QueryableParser {
    CompiledTSQuery compileQuery(String queryText);

    CompiledTSQuery compileQuery(String queryId, String queryText);

    QueryResult query(String queryText);

    QueryResult query(CompiledTSQuery compiledTSQuery);

    ParseSession getLatestParseSession();
}
