package org.vstu.meaningtree.languages;

import org.vstu.meaningtree.languages.query.CompiledTSQuery;
import org.vstu.meaningtree.languages.query.ParseSession;
import org.vstu.meaningtree.languages.query.QueryResult;

public interface QueryableParser {
    CompiledTSQuery compileQuery(String queryText);

    CompiledTSQuery compileQuery(String queryId, String queryText);

    QueryResult query(String queryText);

    QueryResult query(CompiledTSQuery compiledTSQuery);

    ParseSession getLatestParseSession();
}
