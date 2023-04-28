from contextlib import contextmanager
from sqlalchemy.orm import scoped_session

"""
Manage Transaction session.begin, session.rollback, session.commit, session.close

https://stackoverflow.com/questions/14799189/avoiding-boilerplate-session-handling-code-in-sqlalchemy-functions
Avoiding boilerplate session handling code in sqlalchemy functions
"""


@contextmanager
def transaction(sess: scoped_session):
    # if not sess.is_active:
    sess.begin()
    try:
        yield sess
    except Exception:
        sess.rollback()
        raise
    else:
        sess.commit()
    finally:
        sess.close()
