from rest_framework.pagination import CursorPagination, PageNumberPagination


class DyCursorPagination(CursorPagination):
    """
    Cursor-based pagination for author/video collection list.
    Stable ordering: created_at then id (tie-break).
    """

    page_size = 20
    page_size_query_param = "limit"
    max_page_size = 200
    ordering = "-created_at", "-id"


class CustomPageNumberPagination(PageNumberPagination):
    """
    Custom pagination class that supports dynamic page size via 'limit' parameter
    """
    page_size = 20  # Default page size
    page_size_query_param = 'limit'  # Allow client to override page size using 'limit' parameter
    page_query_param = 'page'  # Page number parameter
    max_page_size = 200  # Maximum page size allowed
    
    def get_paginated_response(self, data):
        """
        Return a paginated style Response object with custom format
        """
        from rest_framework.response import Response
        
        return Response({
            'count': self.page.paginator.count,
            'next': self.get_next_link(),
            'previous': self.get_previous_link(),
            'results': data
        })

