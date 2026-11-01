package com.know.entities.application;

import static com.know.entities.application.TvModels.*;

import java.util.List;

public interface TvProvider {
  List<Candidate> search(String query);

  Show show(long id);

  Catalog catalog(long id);
}
