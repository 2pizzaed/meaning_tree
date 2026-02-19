package org.vstu.meaningtree.languages.helpers.query;

import org.treesitter.TSQuery;

public final class CompiledTSQuery {
    private final String id;
    private final String queryText;
    private final TSQuery query;

    public CompiledTSQuery(String id, String queryText, TSQuery query) {
        this.id = id;
        this.queryText = queryText;
        this.query = query;
    }

    public String getId() {
        return id;
    }

    public String getQueryText() {
        return queryText;
    }

    public TSQuery getQuery() {
        return query;
    }
}
