// Here we define what we need.
// Webpack is gonna use this and produce a bundle.
// cljs will read this bundle and provide the namespaces for us.
import React from 'react';
import ReactDOM from 'react-dom';
import Select from 'react-select';
import ReactTable from 'react-table'

window.React = React;
window.ReactDOM = ReactDOM;
window.Select = Select;
window.ReactTable = ReactTable
