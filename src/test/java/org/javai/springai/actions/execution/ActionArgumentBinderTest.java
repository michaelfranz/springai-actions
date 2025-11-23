package org.javai.springai.actions.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.karakun.exo_star.sql.dsl.model.Query;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import org.javai.springai.actions.api.ActionContext;
import org.javai.springai.actions.api.FromContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ActionArgumentBinderTest {

	private static final String QUERY_AS_JSON = """
			{
			  "fromClause" : {
			    "table" : {
			      "tableName" : "FCT_ORDERS",
			      "alias" : "f"
			    }
			  },
			  "joinClauses" : [ {
			    "joinedTable" : {
			      "tableName" : "DIM_CUSTOMERS",
			      "alias" : "c"
			    },
			    "joinCondition" : {
			      "leftColumn" : {
			        "columnName" : "id",
			        "tableAlias" : "c"
			      },
			      "rightColumn" : {
			        "columnName" : "customer_id",
			        "tableAlias" : "f"
			      }
			    }
			  }, {
			    "joinedTable" : {
			      "tableName" : "DIM_DATES",
			      "alias" : "d"
			    },
			    "joinCondition" : {
			      "leftColumn" : {
			        "columnName" : "id",
			        "tableAlias" : "d"
			      },
			      "rightColumn" : {
			        "columnName" : "date_id",
			        "tableAlias" : "f"
			      }
			    }
			  } ],
			  "selectClause" : {
			    "items" : [ {
			      "simple" : {
			        "expression" : {
			          "column" : {
			            "columnName" : "customer_name",
			            "tableAlias" : "c"
			          }
			        },
			        "alias" : "customer_name"
			      }
			    }, {
			      "simple" : {
			        "expression" : {
			          "functionName" : "DATE_TRUNC",
			          "arguments" : [ {
			            "literal" : {
			              "value" : "month"
			            }
			          }, {
			            "column" : {
			              "columnName" : "full_date",
			              "tableAlias" : "d"
			            }
			          } ]
			        }
			      },
			      "alias" : "month"
			    }, {
			      "simple" : {
			        "expression" : {
			          "functionName" : "SUM",
			          "arguments" : [ {
			            "column" : {
			              "columnName" : "order_amount",
			              "tableAlias" : "f"
			            }
			          } ]
			        },
			        "alias" : "total_order_value"
			      }
			    } ]
			  },
			  "whereClause" : {
			    "condition" : {
			      "functionName" : "AND",
			      "arguments" : [ {
			        "functionName" : ">",
			        "arguments" : [ {
			          "column" : {
			            "columnName" : "order_amount",
			            "tableAlias" : "f"
			          }
			        }, {
			          "literal" : {
			            "value" : "1000"
			          }
			        } ]
			      }, {
			        "functionName" : ">=",
			        "arguments" : [ {
			          "column" : {
			            "columnName" : "full_date",
			            "tableAlias" : "d"
			          }
			        }, {
			          "functionName" : "DATE_TRUNC",
			          "arguments" : [ {
			            "literal" : {
			              "value" : "month"
			            }
			          }, {
			            "functionName" : "CURRENT_DATE",
			            "arguments" : [ ]
			          } ]
			        } ]
			      }, {
			        "functionName" : "<",
			        "arguments" : [ {
			          "column" : {
			            "columnName" : "full_date",
			            "tableAlias" : "d"
			          }
			        }, {
			          "functionName" : "DATE_TRUNC",
			          "arguments" : [ {
			            "literal" : {
			              "value" : "month"
			            }
			          }, {
			            "functionName" : "CURRENT_DATE",
			            "arguments" : [ ]
			          } ]
			        } ]
			      } ]
			    }
			  },
			  "groupByClause" : {
			    "columns" : [ {
			      "columnName" : "customer_name",
			      "tableAlias" : "c"
			    }, {
			      "columnName" : "full_date",
			      "tableAlias" : "d"
			    } ]
			  },
			  "orderByClause" : {
			    "items" : [ {
			      "column" : {
			        "columnName" : "total_order_value",
			        "tableAlias" : null
			      },
			      "direction" : "DESC"
			    } ]
			  }
			}""";


	private final ObjectMapper mapper = new ObjectMapper();
	private final ActionArgumentBinder binder = new ActionArgumentBinder();

	@Test
	void bindArgumentsResolvesContextAndJsonValues() throws Exception {
		Method method = BinderSample.class.getMethod("fullAction", ActionContext.class, String.class, int.class, String.class);
		Parameter[] parameters = method.getParameters();

		ObjectNode args = mapper.createObjectNode();
		args.put(parameters[2].getName(), 5);
		args.put(parameters[3].getName(), "details");

		ActionContext ctx = new ActionContext();
		ctx.put("user", "Alice");

		Object[] bound = binder.bindArguments(method, args, ctx);

		assertThat(bound[0]).isSameAs(ctx);
		assertThat(bound[1]).isEqualTo("Alice");
		assertThat(bound[2]).isEqualTo(5);
		assertThat(bound[3]).isEqualTo("details");
	}

	@Test
	void bindArgumentsThrowsWhenJsonArgumentMissing() throws Exception {
		Method method = BinderSample.class.getMethod("requiresArgument", String.class);
		ActionContext ctx = new ActionContext();

		String expectedParam = method.getParameters()[0].getName();
		assertThatThrownBy(() -> binder.bindArguments(method, mapper.createObjectNode(), ctx))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Missing required argument '%s' for action '%s'".formatted(expectedParam, "requiresArgument"));
	}

	@Test
	@DisplayName("Deserialize QUERY_AS_JSON string constant to Query record instance")
	void deserializeQueryAsJsonToQueryRecord() throws Exception {
		// Act - Attempt to deserialize the JSON string constant into a Query record
		Query query = mapper.readValue(QUERY_AS_JSON, Query.class);

		// Assert - Verify that the Query object was created successfully
		assertThat(query).isNotNull();
		assertThat(query.selectClause()).isNotNull();
		assertThat(query.fromClause()).isNotNull();
	}

	static class BinderSample {

		public void fullAction(ActionContext context, @FromContext("user") String username, int quantity, String details) {
		}

		public void requiresArgument(String value) {
		}
	}
}

