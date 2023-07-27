import logging
from typing import Dict, List, Optional

import pandas as pd
from pandas.core.frame import DataFrame

from sqlalchemy.sql import text
from sqlalchemy.orm import scoped_session
from sqlalchemy.exc import DatabaseError


def getDataFrameFromQuery(scoped_sess: scoped_session,
                          raw_query: str,
                          query_params: Dict,
                          parse_dates: Optional[List[str]],
                          logger: logging.Logger = None) -> DataFrame:
    """
    引数のSQLクエリーとクエリーパラメータからDataFrameを生成する
    :param scoped_sess: SQLAlchemy scoped session object.
    :param raw_query: RAW query.
    :param query_params: Query parameter dictionary.
    :param parse_dates: parsing date column list is optional.
    :param logger: application logger is optional.
    :return: pandas.DataFrame
    :raise DatabaseError
    """
    try:
        with scoped_sess.connection() as conn:
            read_df = pd.read_sql(
                text(raw_query), conn,
                params=query_params,
                parse_dates=parse_dates
            )
        return read_df
    except DatabaseError as err:
        if logger is not None:
            logger.warning(f"type:{type(err)}, {err}")
        raise err
    finally:
        scoped_sess.close()
