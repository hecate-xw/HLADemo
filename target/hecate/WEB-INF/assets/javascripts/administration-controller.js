/**
 * Created by Gaea on 3/24/2016.
 */
angular.module('HLADemo').controller('AdministrationController', ['$http', '$scope', '$cookies', function($http, $scope, $cookies) {
    var ctrl = this;
    $scope.request = {"crcAddress":"localhost", "federationName": "", "federateName": "", "id": sessionStorage.getItem('id')};
    console.log(JSON.stringify($scope.request));

    $scope.clickToUpload = function(src) {
        $scope.source = src;
        document.getElementById('fileToUpload').click();
    };

    $scope.create = function() {
        $http({method: "POST", url: "/federates", data: JSON.stringify($scope.request)}).success(function(data) {
            console.log(data);
        });
    };

    $scope.delete = function() {
        $http({method: "Delete", url: "/federates/" + $scope.request.id }).success(function(data) {
           console.log(data);
        });
    };

    $scope.upload = function (files) {
        //$scope.request.fomFile = files[0];
    };
}]);
